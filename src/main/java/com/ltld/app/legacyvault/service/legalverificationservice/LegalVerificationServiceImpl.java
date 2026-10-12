package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.exception.ConflictOfInterestException;
import com.ltld.app.legacyvault.exception.InvalidDateRangeException;
import com.ltld.app.legacyvault.exception.InvalidSignCodeException;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.RequestNotApprovedException;
import com.ltld.app.legacyvault.exception.TooManyOtpRequestsException;
import com.ltld.app.legacyvault.exception.VaultStateConflictException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalVerificationServiceImpl implements LegalVerificationService {

    private static final int CLAIM_TIMEOUT_DAYS = 60; // BR-04
    private static final int SIGN_OTP_EXPIRE_MINUTES = 5;
    private static final int MAX_SIGN_ATTEMPTS = 5;
    private static final long SIGN_OTP_RESEND_COOLDOWN_SECONDS = 60;

    private final LegalVerificationRequestRepository requestRepository;
    private final DigitalSignatureRepository signatureRepository;
    private final VerificationRequestVaultRepository requestVaultRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;

    // UC12: danh sách hồ sơ đang chờ duyệt (Q23-b + N6: loại trừ xung đột lợi ích)
    @Override
    @Transactional(readOnly = true)
    public List<VerificationRequestResponseDto> getPendingRequests(UUID verifierId) {
        return requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId)
                .stream().map(this::toDto).toList();
    }

    // UC12: xem chi tiết 1 hồ sơ (N6: trả 404 nếu verifier là owner hoặc executor để không lộ thông tin)
    @Override
    @Transactional(readOnly = true)
    public VerificationRequestResponseDto getById(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertNotConflictOfInterestForRead(request, verifierId);
        return toDto(request);
    }

    // UC13: xác thực tài liệu (mock) (N6: trả 404 nếu xung đột lợi ích)
    @Override
    @Transactional(readOnly = true)
    public DocumentVerificationResponseDto verifyDocument(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertNotConflictOfInterestForRead(request, verifierId);
        boolean valid = mockVerifyDocument(request.getDeathCertificateFileUrlEncrypted());

        return DocumentVerificationResponseDto.builder()
                .requestId(requestId)
                .valid(valid)
                .message(valid
                        ? "Tài liệu hợp lệ"
                        : "Tài liệu không hợp lệ (chỉ chấp nhận .pdf, .jpg, .png)")
                .build();
    }

    // UC12: phê duyệt hồ sơ (chưa mở khóa Vault)
    @Override
    @Transactional
    public VerificationRequestResponseDto approve(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);
        assertNoConflictOfInterest(request, verifierId);

        request.setStatus(LegalVerificationStatus.APPROVED);
        request.setVerifier(userRepository.getReferenceById(verifierId)); // M5: dùng getReferenceById
        request.setDecidedAt(LocalDateTime.now());
        requestRepository.save(request);

        auditLogService.log(AuditAction.LEGAL_REQUEST_APPROVED, AuditResult.SUCCESS,
                verifierId, null,
                "LegalVerificationRequest", requestId.toString(), null);

        return toDto(request);
    }

    // UC12: từ chối hồ sơ
    @Override
    @Transactional
    public VerificationRequestResponseDto reject(UUID requestId, UUID verifierId,
                                                 RejectVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);
        assertNoConflictOfInterest(request, verifierId);

        request.setStatus(LegalVerificationStatus.REJECTED);
        request.setVerifier(userRepository.getReferenceById(verifierId)); // M5: dùng getReferenceById
        request.setRejectionReason(dto.getReason());
        request.setDecidedAt(LocalDateTime.now());
        requestRepository.save(request);

        auditLogService.log(AuditAction.LEGAL_REQUEST_REJECTED, AuditResult.SUCCESS,
                verifierId, null,
                "LegalVerificationRequest", requestId.toString(), "reason=" + dto.getReason());

        return toDto(request);
    }

    // UC14: gửi OTP ký số qua email của Legal Verifier đã duyệt hồ sơ
    @Override
    @Transactional
    public void sendSignOtp(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);

        if (request.getStatus() != LegalVerificationStatus.APPROVED) {
            throw new RequestNotApprovedException("Hồ sơ phải được phê duyệt trước khi yêu cầu mã ký.");
        }
        if (request.getVerifier() == null || !verifierId.equals(request.getVerifier().getId())) {
            throw new NotAssignedVerifierException("Chỉ Legal Verifier đã phê duyệt hồ sơ mới được yêu cầu mã ký.");
        }
        if (signatureRepository.existsByRequestId(requestId)) {
            throw new RequestAlreadyReviewedException("Hồ sơ này đã được ký trước đó.");
        }

        User verifier = userRepository.findById(verifierId)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Không tìm thấy thông tin verifier."));

        String otp = otpGenerator.generate();
        LocalDateTime now = LocalDateTime.now();

        // N1: cooldown 60 giây giữa 2 lần gửi mã
        if (request.getSignOtpSentAt() != null) {
            long elapsed = Duration.between(request.getSignOtpSentAt(), now).getSeconds();
            if (elapsed < SIGN_OTP_RESEND_COOLDOWN_SECONDS) {
                throw new TooManyOtpRequestsException(SIGN_OTP_RESEND_COOLDOWN_SECONDS - elapsed);
            }
        }

        request.setSignOtpSentAt(now);
        request.setSignOtpHash(otpHasher.hash(requestId, otp));
        request.setSignOtpExpiresAt(now.plusMinutes(SIGN_OTP_EXPIRE_MINUTES));
        request.setSignAttempts(0); // reset lượt thử khi sinh mã mới
        requestRepository.save(request);

        emailSender.sendEmail(verifier.getEmail(), otp);
        log.info("Sent sign OTP to verifier {} for request {}", verifier.getEmail(), requestId);
    }

    // UC14: ký xác nhận – hồ sơ phải APPROVED, đúng verifier, đúng mã OTP, chưa ký
    @Override
    @Transactional(noRollbackFor = InvalidSignCodeException.class)
    public VerificationRequestResponseDto sign(UUID requestId, UUID verifierId,
                                               SignVerificationRequestDto dto) {
        // N4: Chỉ chấp nhận method MOCK_OTP
        if (dto.getMethod() != SignatureMethod.MOCK_OTP) {
            throw new InvalidSignCodeException("Phương thức ký hiện tại chỉ hỗ trợ MOCK_OTP.");
        }

        // PESSIMISTIC_WRITE: khóa row, tránh 2 request ký song song gây lỗi 500
        LegalVerificationRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException(
                        "Không tìm thấy hồ sơ: " + requestId));

        if (request.getStatus() != LegalVerificationStatus.APPROVED) {
            throw new RequestNotApprovedException("Hồ sơ phải được phê duyệt trước khi ký");
        }
        // Chỉ verifier đã phê duyệt mới được ký (FR-14 bước 1)
        if (request.getVerifier() == null
                || !verifierId.equals(request.getVerifier().getId())) {
            throw new NotAssignedVerifierException(
                    "Chỉ Legal Verifier đã phê duyệt hồ sơ mới được ký.");
        }
        if (signatureRepository.existsByRequestId(requestId)) {
            throw new RequestAlreadyReviewedException("Hồ sơ này đã được ký trước đó");
        }

        // FR-14 bước 3: xác thực OTP ký số
        if (request.getSignOtpHash() == null
                || request.getSignOtpExpiresAt() == null
                || LocalDateTime.now().isAfter(request.getSignOtpExpiresAt())) {
            throw new InvalidSignCodeException("Mã xác nhận đã hết hạn hoặc chưa được tạo. Vui lòng yêu cầu mã mới.");
        }

        request.setSignAttempts(request.getSignAttempts() + 1);
        if (request.getSignAttempts() > MAX_SIGN_ATTEMPTS) {
            request.setSignOtpHash(null); // buộc yêu cầu mã mới
            requestRepository.save(request);
            auditLogService.log(AuditAction.LEGAL_SIGN_FAILED, AuditResult.FAILURE,
                    verifierId, null,
                    "LegalVerificationRequest", requestId.toString(), "Too many sign attempts");
            throw new InvalidSignCodeException("Sai quá số lần cho phép. Vui lòng yêu cầu mã mới.");
        }

        if (!otpHasher.matches(requestId, dto.getCode(), request.getSignOtpHash())) {
            requestRepository.save(request);
            auditLogService.log(AuditAction.LEGAL_SIGN_FAILED, AuditResult.FAILURE,
                    verifierId, null,
                    "LegalVerificationRequest", requestId.toString(), "Wrong sign code");
            throw new InvalidSignCodeException("Mã xác nhận không đúng.");
        }

        // Xác thực thành công: hủy mã OTP đã dùng
        request.setSignOtpHash(null);

        // Mọi Vault phải ACTIVE và thuộc đúng chủ sở hữu; sai 1 cái hủy cả lô
        List<VerificationRequestVault> links =
                requestVaultRepository.findByRequestIdFetchVault(requestId);
        if (links.isEmpty()) {
            throw new VaultStateConflictException("Hồ sơ không gắn với Vault nào.");
        }
        List<Vault> invalid = links.stream()
                .map(VerificationRequestVault::getVault)
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE
                        || v.getOwner() == null
                        || !v.getOwner().getId().equals(request.getOwner().getId()))
                .toList();
        if (!invalid.isEmpty()) {
            throw new VaultStateConflictException(
                    "Có Vault không ở trạng thái cho phép mở khóa: "
                            + invalid.stream().map(v -> String.valueOf(v.getId())).toList());
        }

        // Tạo chữ ký số (M5: dùng getReferenceById)
        DigitalSignature signature = DigitalSignature.builder()
                .request(request)
                .verifier(userRepository.getReferenceById(verifierId))
                .signatureMethod(dto.getMethod())
                .signatureHash(SignatureHasher.hash(
                        requestId, verifierId, dto.getMethod(),
                        request.getDeathCertificateFileUrlEncrypted()))
                .build();
        signatureRepository.save(signature);

        // Mở khóa từng Vault, đặt hạn nhận 60 ngày (BR-04), ghi audit mỗi vault
        LocalDateTime now = LocalDateTime.now();
        for (VerificationRequestVault link : links) {
            Vault vault = link.getVault();
            vault.setStatus(VaultStatus.UNLOCKED);
            vault.setUnlockedAt(now);
            vault.setClaimDeadlineAt(now.plusDays(CLAIM_TIMEOUT_DAYS));

            // N5 / M4: Vault hợp lệ luôn có ID
            auditLogService.log(AuditAction.VAULT_UNLOCK, AuditResult.SUCCESS,
                    verifierId, null,
                    "Vault",
                    vault.getId().toString(),
                    "unlocked by legal verifier, request " + requestId);
        }

        // Chuyển trạng thái hồ sơ → SIGNED
        request.setStatus(LegalVerificationStatus.SIGNED);
        requestRepository.save(request);

        auditLogService.log(AuditAction.LEGAL_REQUEST_SIGNED, AuditResult.SUCCESS,
                verifierId, null,
                "LegalVerificationRequest", requestId.toString(),
                "signatureId=" + signature.getId());

        // FR-14 bước 6: Bắn sự kiện mở khóa để module FR-16/FR-07 thông báo cho Beneficiary & Executor
        List<UUID> unlockedVaultIds = links.stream().map(l -> l.getVault().getId()).toList();
        eventPublisher.publishEvent(new VaultsUnlockedEvent(requestId, unlockedVaultIds));
        log.info("FR-14: Published VaultsUnlockedEvent for request {} with vaults {}", requestId, unlockedVaultIds);

        return toDto(request);
    }

    // UC15: lịch sử hồ sơ đã xử lý, lọc tùy chọn theo trạng thái và khoảng ngày
    @Override
    @Transactional(readOnly = true)
    public List<VerificationRequestResponseDto> getHistory(
            UUID verifierId, LegalVerificationStatus status,
            LocalDate from, LocalDate to) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' phải trước hoặc bằng 'to'.");
        }

        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();

        return requestRepository.findByVerifierIdOrderByDecidedAtDesc(verifierId).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> fromTime == null
                        || (r.getDecidedAt() != null && !r.getDecidedAt().isBefore(fromTime)))
                .filter(r -> toTime == null
                        || (r.getDecidedAt() != null && r.getDecidedAt().isBefore(toTime)))
                .map(this::toDto)
                .toList();
    }

    // Mock FR-13: hợp lệ nếu đường dẫn kết thúc bằng .pdf, .jpg hoặc .png
    private boolean mockVerifyDocument(String documentRef) {
        if (documentRef == null) return false;
        String ref = documentRef.toLowerCase();
        return ref.endsWith(".pdf") || ref.endsWith(".jpg") || ref.endsWith(".png");
    }

    private LegalVerificationRequest findRequestOrThrow(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException(
                        "Không tìm thấy hồ sơ: " + requestId));
    }

    private void assertPending(LegalVerificationRequest request) {
        if (request.getStatus() != LegalVerificationStatus.PENDING) {
            throw new RequestAlreadyReviewedException("Hồ sơ này đã được xử lý trước đó");
        }
    }

    // Verifier không được xử lý hồ sơ của chính mình (là owner hoặc executor)
    private void assertNoConflictOfInterest(LegalVerificationRequest request, UUID verifierId) {
        if (isConflict(request, verifierId)) {
            throw new ConflictOfInterestException(
                    "Bạn không được xử lý hồ sơ có liên quan đến chính mình.");
        }
    }

    // N6 / Q23-b: Khi đọc chi tiết/kiểm tra tài liệu, nếu xung đột lợi ích thì trả 404 để không lộ sự tồn tại
    private void assertNotConflictOfInterestForRead(LegalVerificationRequest request, UUID verifierId) {
        if (isConflict(request, verifierId)) {
            throw new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + request.getId());
        }
    }

    private boolean isConflict(LegalVerificationRequest request, UUID verifierId) {
        return (request.getOwner() != null && verifierId.equals(request.getOwner().getId()))
                || (request.getExecutor() != null && verifierId.equals(request.getExecutor().getId()));
    }

    private VerificationRequestResponseDto toDto(LegalVerificationRequest r) {
        List<UUID> vaultIds = requestVaultRepository.findByRequestId(r.getId())
                .stream()
                .map(rv -> rv.getVault().getId())
                .toList();

        return VerificationRequestResponseDto.builder()
                .id(r.getId())
                .ownerName(r.getOwner().getFullName())
                .executorName(r.getExecutor().getFullName())
                .status(r.getStatus())
                .hasDeathCertificate(r.getDeathCertificateFileUrlEncrypted() != null)
                .vaultIds(vaultIds)
                .rejectionReason(r.getRejectionReason())
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt()) // R1: không để mất createdAt
                .build();
    }
}