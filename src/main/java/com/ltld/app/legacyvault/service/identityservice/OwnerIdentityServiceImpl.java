package com.ltld.app.legacyvault.service.identityservice;

import com.ltld.app.legacyvault.dto.identitydto.*;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.OwnerIdentity;
import com.ltld.app.legacyvault.entity.PhoneOtpChallenge;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.TooManyOtpRequestsException;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.OwnerIdentityRepository;
import com.ltld.app.legacyvault.repository.PhoneOtpChallengeRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.utility.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerIdentityServiceImpl implements OwnerIdentityService {

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration OTP_SEND_WINDOW = Duration.ofHours(1);
    static final int OTP_MAX_SENDS_PER_WINDOW = 5;
    static final int OTP_MAX_ATTEMPTS = 5;
    /** Sửa thông tin đã xác minh: phải xác minh lại số điện thoại trong khoảng này (xác thực lại). */
    static final Duration REVERIFY_WINDOW = Duration.ofMinutes(15);
    static final int MIN_AGE = 18;
    private static final int OCR_MAX_CALLS_PER_MINUTE = 5;

    // Loại giấy tờ trong HMAC (IdentityHasher): cùng số luôn ra cùng hash để tra trùng
    private static final String CITIZEN_ID_KIND = "citizen-id";
    private static final String OLD_ID_KIND = "old-id";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final UserRepository userRepository;
    private final OwnerIdentityRepository identityRepository;
    private final PhoneOtpChallengeRepository otpRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final CryptoService cryptoService;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final IdentityHasher identityHasher;
    private final AuditLogService auditLogService;
    private final UploadedDocumentValidator uploadedDocumentValidator;
    private final CitizenIdOcrClient ocrClient;
    private final Clock clock;

    private final Map<UUID, Deque<Long>> ocrCalls = new ConcurrentHashMap<>();

    // Chưa nối nhà cung cấp SMS: mã trả trong response để thử nghiệm. Tắt khi có SMS thật.
    @Value("${app.kyc.sms-sandbox:true}")
    private boolean smsSandbox;

    // ---------------------------------------------------------------- status

    @Override
    @Transactional(readOnly = true)
    public IdentityStatusResponse getStatus(UUID userId) {
        User user = loadUser(userId);
        return toStatus(user, identityRepository.findByUserId(userId).orElse(null), isLocked(userId));
    }

    // ---------------------------------------------------------------- extract

    @Override
    public IdentityExtractResponse extractFromQr(UUID userId, String qrText) {
        CitizenQrParser.Result qr;
        try {
            qr = CitizenQrParser.parse(qrText);
        } catch (IllegalArgumentException e) {
            throw new UserProfileException(ErrorCode.CITIZEN_QR_INVALID, e.getMessage());
        }
        List<String> warnings = new ArrayList<>();
        String oldId = qr.oldIdNumber();
        if (oldId != null && !CitizenId.isValidOldId(oldId)) {
            oldId = null;   // QR một số thẻ ghi lại chính số CCCD ở trường này
        }
        VnProvince province = VnProvince.fromAddress(qr.address()).orElse(null);
        if (province == null) {
            warnings.add("PROVINCE_NOT_DETECTED");
        }
        return IdentityExtractResponse.builder()
                .source(KycSource.QR.name())
                .citizenId(qr.citizenId())
                .oldIdNumber(oldId)
                .fullName(qr.fullName())
                .dateOfBirth(qr.dateOfBirth())
                .gender(qr.gender() == null ? null : qr.gender().name())
                .residenceProvince(province == null ? null : province.name())
                .warnings(warnings)
                .build();
    }

    @Override
    public IdentityExtractResponse extractFromImage(UUID userId, MultipartFile image, boolean aiOcrConsent) {
        if (!aiOcrConsent) {
            throw new UserProfileException(ErrorCode.KYC_CONSENT_REQUIRED,
                    "Please agree to send the card image to the AI reader first");
        }
        if (!allowOcrCall(userId)) {
            throw new UserProfileException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many card reads in a minute; please wait and try again");
        }
        byte[] bytes = uploadedDocumentValidator.readAndValidate(image);
        CitizenIdOcrClient.Extracted x = ocrClient.read(bytes);

        List<String> warnings = new ArrayList<>();
        if (x.documentType() != null && !"CCCD".equalsIgnoreCase(x.documentType())) {
            warnings.add("NOT_A_CITIZEN_ID_CARD");
        }
        String digits = x.citizenId() == null ? null : x.citizenId().replaceAll("\\D", "");
        if (digits != null && digits.length() != 12) {
            warnings.add("CITIZEN_ID_UNREADABLE");
            digits = null;
        }
        LocalDate dob = parseCardDate(x.dateOfBirth());
        if (x.dateOfBirth() != null && dob == null) {
            warnings.add("DATE_OF_BIRTH_UNREADABLE");
        }
        CitizenId.Gender gender = CitizenQrParser.parseGender(x.gender());
        VnProvince province = VnProvince.fromAddress(x.placeOfResidence()).orElse(null);
        if (province == null) {
            warnings.add("PROVINCE_NOT_DETECTED");
        }
        warnings.add("REVIEW_AI_RESULT");
        return IdentityExtractResponse.builder()
                .source(KycSource.OCR.name())
                .citizenId(digits)
                .fullName(x.fullName() == null ? null : VnText.titleCaseName(x.fullName()))
                .dateOfBirth(dob)
                .gender(gender == null ? null : gender.name())
                .residenceProvince(province == null ? null : province.name())
                .warnings(warnings)
                .build();
    }

    // ---------------------------------------------------------------- phone OTP

    @Override
    @Transactional
    public PhoneOtpResponse sendPhoneOtp(UUID userId, String rawPhone) {
        User user = loadUserForUpdate(userId);
        ensureNotLocked(userId);
        VnPhoneNumber phone = parsePhone(rawPhone);
        if (!smsSandbox) {
            // Chưa có nhà cung cấp SMS thật: không tạo mã, không giả vờ đã gửi
            throw new UserProfileException(ErrorCode.SERVICE_UNAVAILABLE, "SMS delivery is not configured");
        }
        Instant now = clock.instant();

        PhoneOtpChallenge c = otpRepository.findByUserIdForUpdate(userId).orElse(null);
        if (c != null) {
            long sinceLast = Duration.between(c.getSentAt(), now).toSeconds();
            if (sinceLast < OTP_RESEND_COOLDOWN.toSeconds()) {
                throw new TooManyOtpRequestsException(OTP_RESEND_COOLDOWN.toSeconds() - sinceLast);
            }
            boolean windowOpen = Duration.between(c.getWindowStartedAt(), now).compareTo(OTP_SEND_WINDOW) < 0;
            if (windowOpen && c.getWindowSendCount() >= OTP_MAX_SENDS_PER_WINDOW) {
                throw new TooManyOtpRequestsException(Math.max(1,
                        OTP_SEND_WINDOW.toSeconds() - Duration.between(c.getWindowStartedAt(), now).toSeconds()));
            }
            if (!windowOpen) {
                c.setWindowStartedAt(now);
                c.setWindowSendCount(0);
            }
        } else {
            c = PhoneOtpChallenge.builder().userId(userId).windowStartedAt(now).windowSendCount(0).build();
        }

        String code = otpGenerator.generate();
        c.setPhoneE164(phone.e164());
        c.setCodeHash(otpHasher.hash(userId, phone.e164() + ":" + code));
        c.setSentAt(now);
        c.setExpiresAt(now.plus(OTP_TTL));
        c.setAttempts(0);
        c.setWindowSendCount(c.getWindowSendCount() + 1);
        otpRepository.save(c);

        auditLogService.log(AuditAction.OWNER_PHONE_OTP_SENT, AuditResult.SUCCESS, user.getId(), user.getEmail(),
                null, "User", userId.toString(), "carrier=" + phone.carrier() + ";sandbox=" + smsSandbox);
        return PhoneOtpResponse.builder()
                .maskedPhone(phone.masked())
                .carrier(phone.carrier())
                .expiresAt(c.getExpiresAt())
                .resendAfterSeconds(OTP_RESEND_COOLDOWN.toSeconds())
                .sandboxCode(code)
                .build();
    }

    // noRollbackFor: lần nhập sai phải giữ số lần thử đã tăng
    @Override
    @Transactional(noRollbackFor = UserProfileException.class)
    public IdentityStatusResponse verifyPhoneOtp(UUID userId, String code) {
        User user = loadUserForUpdate(userId);
        ensureNotLocked(userId);
        PhoneOtpChallenge c = otpRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new UserProfileException(ErrorCode.OTP_NOT_FOUND, "Request a verification code first"));
        Instant now = clock.instant();
        if (now.isAfter(c.getExpiresAt())) {
            throw new UserProfileException(ErrorCode.OTP_EXPIRED, "The code has expired; request a new one");
        }
        if (c.getAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new UserProfileException(ErrorCode.TOO_MANY_ATTEMPTS, "Too many wrong codes; request a new one");
        }
        if (!otpHasher.matches(userId, c.getPhoneE164() + ":" + code, c.getCodeHash())) {
            c.setAttempts(c.getAttempts() + 1);
            otpRepository.save(c);
            auditLogService.failure(AuditAction.OWNER_PHONE_VERIFIED, user.getId(), user.getEmail(), "wrong code");
            int left = OTP_MAX_ATTEMPTS - c.getAttempts();
            throw new UserProfileException(ErrorCode.OTP_INVALID,
                    "Incorrect code" + (left > 0 ? "; " + left + " attempt(s) left" : "; request a new one"));
        }

        user.setPhoneNumber(c.getPhoneE164());
        user.setPhoneVerifiedAt(now);
        userRepository.save(user);
        otpRepository.delete(c);
        // sandbox=true: mã hiện trên màn hình, chưa chứng minh được Owner giữ số này. Khi nối SMS thật,
        // lọc audit này để yêu cầu các số đã xác minh ở chế độ thử nghiệm xác minh lại.
        auditLogService.log(AuditAction.OWNER_PHONE_VERIFIED, AuditResult.SUCCESS, user.getId(), user.getEmail(),
                null, "User", userId.toString(), "sandbox=" + smsSandbox);

        // Theo dõi gian lận: một số đã xác minh dùng cho nhiều tài khoản (người nhà dùng chung là hợp lệ, chỉ ghi log)
        long others = userRepository.countByPhoneNumberAndPhoneVerifiedAtIsNotNullAndIdNot(c.getPhoneE164(), userId);
        if (others > 0) {
            log.warn("Verified phone of user {} is also verified on {} other account(s)", userId, others);
        }
        return toStatus(user, identityRepository.findByUserId(userId).orElse(null), false);
    }

    // ---------------------------------------------------------------- submit

    @Override
    @Transactional
    public IdentityStatusResponse submit(UUID userId, SubmitIdentityRequest req) {
        User user = loadUserForUpdate(userId);
        if (!req.isDataConsent()) {
            throw new UserProfileException(ErrorCode.KYC_CONSENT_REQUIRED,
                    "Please agree to the personal data processing terms");
        }
        if (req.getSource() == KycSource.OCR && !req.isAiOcrConsent()) {
            throw new UserProfileException(ErrorCode.KYC_CONSENT_REQUIRED,
                    "Please agree to send the card image to the AI reader");
        }
        ensureNotLocked(userId);
        OwnerIdentity existing = identityRepository.findByUserId(userId).orElse(null);
        Instant now = clock.instant();
        requireVerifiedPhone(user, existing != null, now);

        CitizenId citizenId;
        try {
            citizenId = CitizenId.parse(req.getCitizenId());
        } catch (IllegalArgumentException e) {
            throw new UserProfileException(ErrorCode.CITIZEN_ID_INVALID, e.getMessage());
        }
        LocalDate dob = req.getDateOfBirth();
        LocalDate today = LocalDate.now(clock.withZone(VN_ZONE));
        if (dob.getYear() < 1900 || !dob.isBefore(today)) {
            throw new UserProfileException(ErrorCode.VALIDATION_FAILED, "Invalid date of birth");
        }
        if (Period.between(dob, today).getYears() < MIN_AGE) {
            throw new UserProfileException(ErrorCode.IDENTITY_UNDERAGE,
                    "You must be at least " + MIN_AGE + " years old to enable the Dead Man's Switch");
        }
        if (!citizenId.matchesBirthDate(dob)) {
            throw new UserProfileException(ErrorCode.CITIZEN_ID_INVALID,
                    "The citizen ID does not match the year of birth (digits 4-6 encode the birth year)");
        }
        if (!citizenId.matchesGender(req.getGender())) {
            throw new UserProfileException(ErrorCode.CITIZEN_ID_INVALID,
                    "The citizen ID does not match the gender (digit 4 encodes gender)");
        }
        String oldId = normalizeOldId(req.getOldIdNumber(), citizenId);

        String citizenHash = hashCitizenId(citizenId.value());
        identityRepository.findByCitizenIdHash(citizenHash)
                .filter(other -> !other.getUser().getId().equals(userId))
                .ifPresent(other -> {
                    throw new UserProfileException(ErrorCode.CITIZEN_ID_TAKEN,
                            "This citizen ID is already linked to another account");
                });

        String name = collapseSpaces(req.getFullName());
        if (!VnText.sameName(name, user.getFullName())) {
            if (!req.isUpdateAccountName()) {
                throw new UserProfileException(ErrorCode.IDENTITY_NAME_MISMATCH,
                        "The name on the card differs from your account name");
            }
            user.setFullName(name);
        }

        OwnerIdentity identity = existing != null ? existing : OwnerIdentity.builder().user(user).build();
        identity.setCitizenIdEncrypted(encrypt(citizenId.value()));
        identity.setCitizenIdHash(citizenHash);
        identity.setCitizenIdMasked(citizenId.masked());
        identity.setOldIdEncrypted(oldId == null ? null : encrypt(oldId));
        identity.setOldIdHash(oldId == null ? null : hashOldId(oldId));
        identity.setFullName(name);
        identity.setDateOfBirth(dob);
        identity.setGender(req.getGender());
        identity.setResidenceProvince(req.getResidenceProvince());
        identity.setSource(req.getSource());
        identity.setAiOcrConsent(req.isAiOcrConsent());
        identity.setConsentAt(now);
        identity.setVerifiedAt(now);
        identityRepository.save(identity);

        if (user.getKycLevel() == null || user.getKycLevel() == KycLevel.NONE) {
            user.setKycLevel(KycLevel.BASIC);
        }
        userRepository.save(user);

        auditLogService.log(existing == null ? AuditAction.OWNER_IDENTITY_VERIFIED : AuditAction.OWNER_IDENTITY_UPDATED,
                AuditResult.SUCCESS, user.getId(), user.getEmail(), null, "User", userId.toString(),
                "source=" + req.getSource().name());
        return toStatus(user, identity, false);
    }

    // ---------------------------------------------------------------- death certificate match

    @Override
    @Transactional(readOnly = true)
    public OwnerIdMatch matchDocumentId(UUID ownerId, String idNumberOnDocument) {
        String digits = idNumberOnDocument == null ? "" : idNumberOnDocument.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return OwnerIdMatch.NOT_ON_DOCUMENT;
        }
        OwnerIdentity identity = ownerId == null ? null : identityRepository.findByUserId(ownerId).orElse(null);
        if (identity == null) {
            return OwnerIdMatch.OWNER_NOT_VERIFIED;
        }
        // So bản giải mã trong bộ nhớ (không qua HMAC) để đổi khoá hash không làm mọi giấy chứng tử bị báo lệch.
        // Số giải mã không được trả ra ngoài hay ghi log.
        if (digits.length() == 12) {
            String stored = decryptOrNull(identity.getCitizenIdEncrypted());
            if (stored == null) return OwnerIdMatch.UNVERIFIABLE;
            return constantTimeEquals(digits, stored) ? OwnerIdMatch.MATCHED : OwnerIdMatch.MISMATCHED;
        }
        if (digits.length() == 9) {
            String stored = decryptOrNull(identity.getOldIdEncrypted());
            if (stored == null) return OwnerIdMatch.UNVERIFIABLE;
            return constantTimeEquals(digits, stored) ? OwnerIdMatch.MATCHED : OwnerIdMatch.MISMATCHED;
        }
        return OwnerIdMatch.UNVERIFIABLE;
    }

    // ---------------------------------------------------------------- helpers

    private void requireVerifiedPhone(User user, boolean reverify, Instant now) {
        boolean verified = user.getPhoneVerifiedAt() != null && user.getPhoneNumber() != null
                && VnPhoneNumber.tryParse(user.getPhoneNumber()).isPresent();
        if (!verified) {
            throw new UserProfileException(ErrorCode.PHONE_NOT_VERIFIED, "Please verify your phone number first");
        }
        if (reverify && user.getPhoneVerifiedAt().isBefore(now.minus(REVERIFY_WINDOW))) {
            throw new UserProfileException(ErrorCode.PHONE_NOT_VERIFIED,
                    "To change verified identity details, confirm your phone number with a new code");
        }
    }

    private static String normalizeOldId(String raw, CitizenId citizenId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\s", "");
        if (digits.equals(citizenId.value())) {
            return null;
        }
        if (!CitizenId.isValidOldId(digits)) {
            throw new UserProfileException(ErrorCode.CITIZEN_ID_INVALID, "The old ID number must have 9 digits");
        }
        return digits;
    }

    private boolean isLocked(UUID userId) {
        return dmsConfigRepository.findByOwnerId(userId)
                .map(DmsConfig::getStatus)
                .filter(s -> s == DmsStatus.TRIGGERED || s == DmsStatus.PENDING_CONFIRMATION)
                .isPresent();
    }

    private void ensureNotLocked(UUID userId) {
        if (isLocked(userId)) {
            throw new UserProfileException(ErrorCode.IDENTITY_LOCKED,
                    "Identity details cannot be changed while the Dead Man's Switch is being activated");
        }
    }

    private static VnPhoneNumber parsePhone(String raw) {
        try {
            return VnPhoneNumber.parse(raw);
        } catch (VnPhoneNumber.InvalidVnPhoneException e) {
            throw new UserProfileException(ErrorCode.PHONE_INVALID, e.getMessage());
        }
    }

    private boolean allowOcrCall(UUID userId) {
        long now = clock.millis();
        Deque<Long> q = ocrCalls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= OCR_MAX_CALLS_PER_MINUTE) return false;
            q.addLast(now);
            return true;
        }
    }

    static LocalDate parseCardDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim().replace('-', '/').replace('.', '/'), CARD_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String collapseSpaces(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private String hashCitizenId(String digits) {
        return identityHasher.hash(CITIZEN_ID_KIND, digits);
    }

    private String hashOldId(String digits) {
        return identityHasher.hash(OLD_ID_KIND, digits);
    }

    private String decryptOrNull(String cipherText) {
        if (cipherText == null) return null;
        try {
            return cryptoService.decrypt(cipherText);
        } catch (Exception e) {
            log.warn("Cannot decrypt stored identity number: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String encrypt(String plain) {
        try {
            return cryptoService.encrypt(plain);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encrypt identity data", e);
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileException(ErrorCode.NOT_FOUND, "Account not found"));
    }

    private User loadUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserProfileException(ErrorCode.NOT_FOUND, "Account not found"));
    }

    private static IdentityStatusResponse toStatus(User user, OwnerIdentity identity, boolean locked) {
        KycLevel level = user.getKycLevel() != null ? user.getKycLevel() : KycLevel.NONE;
        String phone = user.getPhoneNumber() == null ? null
                : VnPhoneNumber.tryParse(user.getPhoneNumber()).map(VnPhoneNumber::display).orElse(user.getPhoneNumber());
        return IdentityStatusResponse.builder()
                .kycLevel(level.name())
                .verified(level != KycLevel.NONE && identity != null)
                .source(identity == null ? null : identity.getSource().name())
                .citizenIdMasked(identity == null ? null : identity.getCitizenIdMasked())
                .hasOldIdNumber(identity != null && identity.getOldIdHash() != null)
                .fullName(identity == null ? null : identity.getFullName())
                .dateOfBirth(identity == null ? null : identity.getDateOfBirth())
                .gender(identity == null ? null : identity.getGender().name())
                .residenceProvince(identity == null ? null : identity.getResidenceProvince().name())
                .verifiedAt(identity == null ? null : identity.getVerifiedAt())
                .accountFullName(user.getFullName())
                .phoneNumber(phone)
                .phoneVerified(user.getPhoneVerifiedAt() != null)
                .locked(locked)
                .provinces(Arrays.stream(VnProvince.values())
                        .map(p -> new IdentityStatusResponse.ProvinceOption(p.name(), p.displayName()))
                        .toList())
                .build();
    }
}
