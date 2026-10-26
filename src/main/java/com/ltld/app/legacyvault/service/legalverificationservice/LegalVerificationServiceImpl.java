package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationVaultDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerifierSummaryResponseDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.event.VerificationRequestDecidedEvent;
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
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalVerificationServiceImpl implements LegalVerificationService {

    private static final int CLAIM_TIMEOUT_DAYS = 60; // BR-04
    private static final int SIGN_OTP_EXPIRE_MINUTES = 5;
    private static final int MAX_SIGN_ATTEMPTS = 5;
    private static final long SIGN_OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final LocalDateTime HISTORY_MIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime HISTORY_MAX_TIME = LocalDateTime.of(9999, 1, 1, 0, 0);

    private final LegalVerificationRequestRepository requestRepository;
    private final DigitalSignatureRepository signatureRepository;
    private final VerificationRequestVaultRepository requestVaultRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;
    private final DmsConfigRepository dmsConfigRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final VerifierAccessGuard verifierAccessGuard;

    // UC12: danh sách hồ sơ đang chờ duyệt (Q23-b + N6: loại trừ xung đột lợi ích)
    @Override
    @Transactional(readOnly = true)
    public List<VerificationRequestResponseDto> getPendingRequests(UUID verifierId) {
        return toDtos(requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId));
    }

    // UC12: xem chi tiết 1 hồ sơ (404 nếu xung đột lợi ích; đã xử lý -> chỉ verifier được giao)
    @Override
    @Transactional(readOnly = true)
    public VerificationRequestResponseDto getById(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = verifierAccessGuard.requireReadable(requestId, verifierId);
        return toDto(request);
    }

    // UC13: xác thực tài liệu (mock) (cùng luật đọc với getById)
    @Override
    @Transactional(readOnly = true)
    public DocumentVerificationResponseDto verifyDocument(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = verifierAccessGuard.requireReadable(requestId, verifierId);
        boolean valid = mockVerifyDocument(request.getDeathCertificateFileUrlEncrypted());

        return DocumentVerificationResponseDto.builder()
                .requestId(requestId)
                .valid(valid)
                .message(valid
                        ? "Tài liệu hợp lệ"
                        : "Tài liệu không hợp lệ (chỉ chấp nhận .pdf, .jpg, .jpeg, .png)")
                .build();
    }

    // D1: Verifier xem giấy chứng tử
    @Override
    @Transactional(readOnly = true)
    public VerificationDocumentDto getDocument(UUID requestId, UUID verifierId) {
        // Xung đột lợi ích -> 404; đã xử lý -> chỉ verifier được giao (m2: dùng chung guard)
        LegalVerificationRequest request = verifierAccessGuard.requireReadable(requestId, verifierId);

        String email = emailOf(verifierId);
        byte[] plain;
        try {
            plain = cryptoService.decryptBytes(
                    fileStorageService.readFile(request.getDeathCertificateFileUrlEncrypted()));
        } catch (Exception e) {
            log.error("Death certificate read/decrypt failed for request {}", requestId, e);
            auditLogService.log(AuditAction.LEGAL_DOCUMENT_VIEWED, AuditResult.FAILURE,
                    verifierId, email, "LegalVerificationRequest", requestId.toString(),
                    "Read or decrypt failed");
            throw new IllegalStateException("Không thể đọc tài liệu. Vui lòng thử lại sau.");
        }

        String contentType = UploadedDocumentValidator.detectContentType(plain);
        auditLogService.log(AuditAction.LEGAL_DOCUMENT_VIEWED, AuditResult.SUCCESS,
                verifierId, email, "LegalVerificationRequest", requestId.toString(), null);

        return VerificationDocumentDto.builder()
                .content(plain)
                .contentType(contentType)
                .fileName("giay-chung-tu-" + requestId + extensionOf(contentType))
                .build();
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> ".bin";
        };
    }

    // UC12: phê duyệt hồ sơ (chưa mở khóa Vault)
    @Override
    @Transactional
    public VerificationRequestResponseDto approve(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);
        assertNoConflictOfInterest(request, verifierId);
        assertHandoverStillValid(request); // D4: DMS còn TRIGGERED và Executor còn ACTIVE mới được duyệt

        User verifier = userRepository.getReferenceById(verifierId); // M5: dùng getReferenceById
        request.setStatus(LegalVerificationStatus.APPROVED);
        request.setVerifier(verifier);
        request.setDecidedAt(LocalDateTime.now());
        requestRepository.save(request);

        auditLogService.log(AuditAction.LEGAL_REQUEST_APPROVED, AuditResult.SUCCESS,
                verifierId, verifier.getEmail(),
                "LegalVerificationRequest", requestId.toString(), null);
        eventPublisher.publishEvent(new VerificationRequestDecidedEvent(
                requestId, LegalVerificationStatus.APPROVED, null)); // B-04

        return toDto(request);
    }

    // UC12: từ chối hồ sơ. B-02: hồ sơ APPROVED chưa ký vẫn được từ chối, nhưng chỉ bởi verifier đã duyệt
    @Override
    @Transactional
    public VerificationRequestResponseDto reject(UUID requestId, UUID verifierId,
                                                 RejectVerificationRequestDto dto) {
        LegalVerificationRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException(
                        "Không tìm thấy hồ sơ: " + requestId));
        assertPendingOrApprovedUnsigned(request, verifierId);
        assertNoConflictOfInterest(request, verifierId);

        User verifier = userRepository.getReferenceById(verifierId); // M5: dùng getReferenceById
        request.setStatus(LegalVerificationStatus.REJECTED);
        request.setVerifier(verifier);
        request.setSignOtpHash(null);
        request.setSignOtpExpiresAt(null);
        request.setSignOtpSentAt(null);
        request.setSignAttempts(0);
        request.setRejectionReason(dto.getReason());
        request.setDecidedAt(LocalDateTime.now());
        requestRepository.save(request);

        auditLogService.log(AuditAction.LEGAL_REQUEST_REJECTED, AuditResult.SUCCESS,
                verifierId, verifier.getEmail(),
                "LegalVerificationRequest", requestId.toString(), "reason=" + dto.getReason());
        eventPublisher.publishEvent(new VerificationRequestDecidedEvent(
                requestId, LegalVerificationStatus.REJECTED, dto.getReason())); // B-04

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

        // D3 (n1): không gửi mã ký cho hồ sơ không thể ký, cũng không tốn lượt cooldown 60 giây
        assertHandoverStillValid(request);

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

        // N3: ghi audit việc gửi mã ký
        auditLogService.log(AuditAction.LEGAL_SIGN_OTP_SENT, AuditResult.SUCCESS,
                verifierId, verifier.getEmail(),
                "LegalVerificationRequest", requestId.toString(), null);

        // N2: chỉ gửi email SAU KHI transaction commit, tránh gửi mã không dùng được nếu commit lỗi
        String email = verifier.getEmail();
        runAfterCommit(() -> {
            emailSender.sendEmail(email, otp);
            log.info("Sent sign OTP to verifier {} for request {}", email, requestId);
        });
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

        // D3 (n1): kiểm tra DMS và Executor trước khi xác thực OTP,
        // để Verifier biết ngay hồ sơ không ký được, khỏi nhập mã rồi mới bị báo lỗi
        assertHandoverStillValid(request);

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
                    verifierId, emailOf(verifierId),
                    "LegalVerificationRequest", requestId.toString(), "Too many sign attempts");
            throw new InvalidSignCodeException("Sai quá số lần cho phép. Vui lòng yêu cầu mã mới.");
        }

        if (!otpHasher.matches(requestId, dto.getCode(), request.getSignOtpHash())) {
            requestRepository.save(request);
            auditLogService.log(AuditAction.LEGAL_SIGN_FAILED, AuditResult.FAILURE,
                    verifierId, emailOf(verifierId),
                    "LegalVerificationRequest", requestId.toString(), "Wrong sign code");
            int remaining = MAX_SIGN_ATTEMPTS - request.getSignAttempts();
            throw new InvalidSignCodeException(remaining > 0
                    ? "Mã xác nhận không đúng. Còn " + remaining + " lần thử."
                    : "Mã xác nhận không đúng. Đã hết lượt thử, vui lòng yêu cầu mã mới.");
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
        String verifierEmail = emailOf(verifierId);
        LocalDateTime now = LocalDateTime.now();
        for (VerificationRequestVault link : links) {
            Vault vault = link.getVault();
            vault.setStatus(VaultStatus.UNLOCKED);
            vault.setUnlockedAt(now);
            vault.setClaimDeadlineAt(now.plusDays(CLAIM_TIMEOUT_DAYS));

            // N5 / M4: Vault hợp lệ luôn có ID
            auditLogService.log(AuditAction.VAULT_UNLOCK, AuditResult.SUCCESS,
                    verifierId, verifierEmail,
                    "Vault",
                    vault.getId().toString(),
                    "unlocked by legal verifier, request " + requestId);
        }

        // Chuyển trạng thái hồ sơ → SIGNED
        request.setStatus(LegalVerificationStatus.SIGNED);
        requestRepository.save(request);

        auditLogService.log(AuditAction.LEGAL_REQUEST_SIGNED, AuditResult.SUCCESS,
                verifierId, verifierEmail,
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
            LocalDate from, LocalDate to, int page, int size) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' phải trước hoặc bằng 'to'.");
        }

        LocalDateTime fromTime = from == null ? HISTORY_MIN_TIME : from.atStartOfDay();
        LocalDateTime toTime = to == null ? HISTORY_MAX_TIME : to.plusDays(1).atStartOfDay();
        Collection<LegalVerificationStatus> statuses = status == null
                ? List.of(LegalVerificationStatus.values())
                : List.of(status);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_PAGE_SIZE);

        return toDtos(requestRepository.findHistory(
                verifierId, statuses, fromTime, toTime, PageRequest.of(safePage, safeSize)));
    }

    // Đếm theo trạng thái ngay trong DB (không phụ thuộc phân trang)
    @Override
    @Transactional(readOnly = true)
    public VerifierSummaryResponseDto getSummary(UUID verifierId) {
        return VerifierSummaryResponseDto.builder()
                .pending(requestRepository.countPendingVisibleTo(LegalVerificationStatus.PENDING, verifierId))
                .awaitingSignature(requestRepository.countByVerifierIdAndStatus(verifierId, LegalVerificationStatus.APPROVED))
                .signed(requestRepository.countByVerifierIdAndStatus(verifierId, LegalVerificationStatus.SIGNED))
                .rejected(requestRepository.countByVerifierIdAndStatus(verifierId, LegalVerificationStatus.REJECTED))
                .build();
    }

    // Mock FR-13: hợp lệ nếu đường dẫn kết thúc bằng .pdf, .jpg, .jpeg hoặc .png
    private boolean mockVerifyDocument(String documentRef) {
        if (documentRef == null) return false;
        String ref = documentRef.toLowerCase();
        return ref.endsWith(".pdf") || ref.endsWith(".jpg")
                || ref.endsWith(".jpeg") || ref.endsWith(".png");
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

    // B-02: PENDING thì ai cũng từ chối được; APPROVED (chưa ký) chỉ verifier đã duyệt mới được từ chối
    private void assertPendingOrApprovedUnsigned(LegalVerificationRequest request, UUID verifierId) {
        if (request.getStatus() == LegalVerificationStatus.PENDING) {
            return;
        }
        if (request.getStatus() == LegalVerificationStatus.APPROVED) {
            User approver = request.getVerifier();
            if (approver == null || !approver.getId().equals(verifierId)) {
                throw new NotAssignedVerifierException("Hồ sơ này đã được Verifier khác duyệt");
            }
            return;
        }
        throw new RequestAlreadyReviewedException("Hồ sơ này đã được xử lý trước đó");
    }

    // D3: kiểm tra lại ngay trước khi mở khóa (phòng dữ liệu bị chèn tay hoặc đổi sau khi duyệt)
    private void assertHandoverStillValid(LegalVerificationRequest request) {
        UUID ownerId = request.getOwner().getId();

        boolean dmsTriggered = dmsConfigRepository.findByOwnerId(ownerId)
                .map(c -> c.getStatus() == DmsStatus.TRIGGERED)
                .orElse(false);
        if (!dmsTriggered) {
            throw new VaultStateConflictException("Dead Man's Switch của Owner chưa được kích hoạt.");
        }

        boolean executorActive = executorAssignmentRepository.existsByOwnerIdAndExecutorUserIdAndStatus(
                ownerId, request.getExecutor().getId(), ExecutorStatus.ACTIVE);
        if (!executorActive) {
            throw new VaultStateConflictException("Executor của hồ sơ không còn quyền hoạt động với Owner này.");
        }
    }

    // Verifier không được xử lý hồ sơ của chính mình (là owner hoặc executor)
    private void assertNoConflictOfInterest(LegalVerificationRequest request, UUID verifierId) {
        if (VerifierAccessGuard.isConflict(request, verifierId)) {
            throw new ConflictOfInterestException(
                    "Bạn không được xử lý hồ sơ có liên quan đến chính mình.");
        }
    }

    // Gửi/ghi sau khi transaction commit; ngoài transaction (unit test) thì chạy ngay
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // M3: lấy email người thao tác để ghi audit
    private String emailOf(UUID userId) {
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }

    // 1 hồ sơ: lấy vault riêng
    private VerificationRequestResponseDto toDto(LegalVerificationRequest r) {
        return toDto(r, requestVaultRepository.findByRequestId(r.getId()).stream()
                .map(LegalVerificationServiceImpl::toVaultDto)
                .toList());
    }

    // M1: nhiều hồ sơ: lấy vault của tất cả trong 1 query
    private List<VerificationRequestResponseDto> toDtos(List<LegalVerificationRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = requests.stream().map(LegalVerificationRequest::getId).toList();
        Map<UUID, List<VerificationVaultDto>> vaultsByRequest = requestVaultRepository.findByRequestIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        rv -> rv.getRequest().getId(),
                        Collectors.mapping(LegalVerificationServiceImpl::toVaultDto, Collectors.toList())));
        return requests.stream()
                .map(r -> toDto(r, vaultsByRequest.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    private static VerificationVaultDto toVaultDto(VerificationRequestVault rv) {
        return VerificationVaultDto.builder()
                .id(rv.getVault().getId())
                .name(rv.getVault().getName())
                .build();
    }

    private VerificationRequestResponseDto toDto(LegalVerificationRequest r, List<VerificationVaultDto> vaults) {
        return VerificationRequestResponseDto.builder()
                .id(r.getId())
                .ownerName(r.getOwner().getFullName())
                .executorName(r.getExecutor().getFullName())
                .status(r.getStatus())
                .hasDeathCertificate(r.getDeathCertificateFileUrlEncrypted() != null)
                .vaultIds(vaults.stream().map(VerificationVaultDto::getId).toList())
                .vaults(vaults)
                .rejectionReason(r.getRejectionReason())
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt()) // R1: không để mất createdAt
                .build();
    }
}
