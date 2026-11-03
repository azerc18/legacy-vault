package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.*;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchema;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchemaRegistry;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final int VERIFICATION_TTL_MINUTES = 15;
    private static final String NOT_FOUND_MSG = "Claim request not found.";
    // Trùng với BeneficiaryNotificationListener.ENTITY_VAULT
    private static final String REF_VAULT = "Vault";
    private static final int CLAIM_TIMEOUT_DAYS = 60;

    // FR-17: các tham số dễ chỉnh. SRS không quy định con số cụ thể.
    private static final int OTP_TTL_MINUTES = 5;
    // FR-17: only reuse a PENDING session that still has room for a full OTP
    private static final int SESSION_REUSE_WINDOW_MINUTES = VERIFICATION_TTL_MINUTES - OTP_TTL_MINUTES;
    private static final int OTP_RESEND_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    // FR-17 3b: khóa tạm; hết hạn sau LOCK_HOURS (cùng cửa sổ 24 giờ với bộ đếm lượt thử)
    private static final int LOCK_HOURS = 24;
    private static final int VIEW_SESSION_MINUTES = 30;

    private static final String ALREADY_CLAIMED_MSG = "These assets have already been claimed.";
    private static final String CLAIM_ENDED_MSG = "The claim period has ended.";
    private static final String NOT_READY_MSG = "The assets are not ready to be claimed yet.";
    private static final String NO_SESSION_MSG = "No verification session. Please start a claim request first.";
    private static final String LOCKED_MSG = "Access is temporarily locked after too many failed verification attempts. Please try again in 24 hours or contact an admin for help.";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> DOSSIER_MASKED_KEYS = Set.of("accountNumber", "accountId");
    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss '(GMT+7)'");
    private static final String CONFIRM_REQUIRED_MSG = "Final confirmation is required to close the handover case.";
    // Dùng chung cho tên tệp và Content-Type; khớp các loại FR-04 cho phép (PDF, DOC/DOCX, JPG/PNG)
    private static final Map<String, String> DOCUMENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final FileStorageService fileStorageService;
    private final AssetAccessRecordRepository assetAccessRecordRepository;
    private final CryptoService cryptoService;
    private final MockKycVerifier mockKycVerifier;
    private final EmailSender emailSender;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final AuditLogService auditLogService;
    private final NotificationRepository notificationRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final AssetAllocationRepository assetAllocationRepository;
    private final JointConsensusService jointConsensusService;   // X1
    private final DigitalSignatureRepository digitalSignatureRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;

    private LocalDateTime resolveDeadline(Vault vault) {
        if (vault.getClaimDeadlineAt() != null) {
            return vault.getClaimDeadlineAt();
        }
        LocalDateTime base = vault.getUnlockedAt() != null ? vault.getUnlockedAt() : LocalDateTime.now();
        return base.plusDays(CLAIM_TIMEOUT_DAYS);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryVaultSummaryResponse> listMyVaults(UUID currentUserId) {
        // Luôn lọc theo người đang đăng nhập, không nhận beneficiaryId từ client
        List<Vault> vaults = vaultRepository.findVaultsOfBeneficiary(
                currentUserId, List.of(VaultStatus.UNLOCKED, VaultStatus.CLAIMED, VaultStatus.ARCHIVED_LOCKED));
        if (vaults.isEmpty()) {
            return List.of();
        }

        Map<UUID, BeneficiaryClaim> claimsByVaultId = claimRepository
                .findByVaultIdInAndBeneficiaryId(vaults.stream().map(Vault::getId).toList(), currentUserId).stream()
                .collect(Collectors.toMap(c -> c.getVault().getId(), c -> c));

        Map<UUID, BeneficiaryInviteStatus> myStatusByVaultId = vaultBeneficiaryRepository
                .findByUserIdAndVaultIdIn(currentUserId, vaults.stream().map(Vault::getId).toList()).stream()
                .collect(Collectors.toMap(b -> b.getVault().getId(), VaultBeneficiary::getStatus, (a, b) -> a));

        return vaults.stream().map(v -> {
            BeneficiaryClaim claim = claimsByVaultId.get(v.getId());
            return BeneficiaryVaultSummaryResponse.builder()
                    .vaultId(v.getId())
                    .vaultName(v.getName())
                    .ownerName(v.getOwner() != null ? v.getOwner().getFullName() : null)
                    .vaultStatus(v.getStatus())
                    .claimStatus(claim != null ? claim.getStatus() : null)
                    .myStatus(myStatusByVaultId.get(v.getId()))
                    .unlockedAt(v.getUnlockedAt())
                    .claimDeadlineAt(v.getClaimDeadlineAt())
                    .claimedAt(claim != null ? claim.getClaimedAt() : null)
                    .build();
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryNotificationResponse> listMyNotifications(UUID currentUserId) {
        return notificationRepository
                .findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(currentUserId, NotificationChannel.IN_APP)
                .stream()
                .map(n -> BeneficiaryNotificationResponse.builder()
                        .id(n.getId())
                        .type(n.getType())
                        .content(n.getContent())
                        .vaultId(REF_VAULT.equals(n.getReferenceType()) ? n.getReferenceId() : null)
                        .read(n.getStatus() == NotificationStatus.READ)
                        .createdAt(n.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void markNotificationRead(UUID notificationId, UUID currentUserId) {
        // Thông báo của người khác trả 404 để không lộ sự tồn tại
        Notification notification = notificationRepository
                .findByIdAndRecipientIdAndChannel(notificationId, currentUserId, NotificationChannel.IN_APP)
                .orElseThrow(() -> new BeneficiaryException("Notification not found.", HttpStatus.NOT_FOUND));
        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ);
            notificationRepository.save(notification);
        }
    }

    // ===================== FR-16 =====================

    @Override
    @Transactional
    public BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request, UUID currentUserId) {

        // 1. Kiểm tra Vault và Phân quyền (Ngăn lộ Vault ID)
        Vault vault = vaultRepository.findById(request.getVaultId())
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));

        User beneficiary = requireBeneficiary(vault.getId(), currentUserId).getUser();

        // 2. Kiểm tra trạng thái Vault
        if (vault.getStatus() == VaultStatus.CLAIMED) {
            throw new BeneficiaryException("These assets have already been claimed.", HttpStatus.CONFLICT);
        }
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BeneficiaryException("The claim period has ended.", HttpStatus.GONE);
        }
        if (vault.getStatus() != VaultStatus.UNLOCKED) {
            throw new BeneficiaryException("The assets are not ready to be claimed yet.", HttpStatus.BAD_REQUEST);
        }

        // 3. Khởi tạo hoặc lấy BeneficiaryClaim hiện tại
        BeneficiaryClaim claim = claimRepository.findByVaultIdAndBeneficiaryId(vault.getId(), currentUserId).orElse(null);

        if (claim != null) {
            if (claim.getStatus() == ClaimStatus.CLAIMED) {
                throw new BeneficiaryException("These assets have already been claimed.", HttpStatus.CONFLICT);
            }
        } else {
            claim = BeneficiaryClaim.builder()
                    .vault(vault)
                    .beneficiary(beneficiary)
                    .status(ClaimStatus.PENDING)
                    .claimDeadlineAt(resolveDeadline(vault))
                    .build();
            claim = claimRepository.save(claim);
        }

        if (claim.getStatus() == ClaimStatus.EXPIRED || LocalDateTime.now().isAfter(claim.getClaimDeadlineAt())) {
            throw new BeneficiaryException("The claim period has ended.", HttpStatus.GONE);
        }

        if (request.getVerificationMethod() == VerificationMethod.EKYC_MOCK && !mockKycVerifier.isEnabled()) {
            throw new BeneficiaryException("The eKYC method is not supported yet.", HttpStatus.BAD_REQUEST);
        }

        // 4. Chống spam: chỉ dùng lại phiên PENDING còn đủ thời gian cho một OTP trọn vẹn.
        //    Phiên cũ hơn mốc này không dùng lại (sendIdentityOtp sẽ từ chối), nên tạo phiên mới.
        //    Phiên cũ vẫn là PENDING để lượt thử sai của nó còn được cộng vào giới hạn 5 lần / 24h.
        LocalDateTime reusableAfter = LocalDateTime.now().minusMinutes(SESSION_REUSE_WINDOW_MINUTES);
        IdentityVerification verification = verificationRepository
                .findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        vault.getId(), currentUserId, request.getVerificationMethod(),
                        VerificationStatus.PENDING, reusableAfter)
                .orElseGet(() -> verificationRepository.save(
                        IdentityVerification.builder()
                                .beneficiary(beneficiary)
                                .vault(vault)
                                .method(request.getVerificationMethod())
                                .status(VerificationStatus.PENDING)
                                .build()));

        // 5. Trả về kết quả kèm verificationId
        return BeneficiaryClaimResponse.builder()
                .claimId(claim.getId())
                .vaultId(vault.getId())
                .verificationId(verification.getId())
                .status(claim.getStatus())
                .claimDeadlineAt(claim.getClaimDeadlineAt())
                .claimedAt(claim.getClaimedAt())
                .build();
    }

    // ===================== FR-17 =====================

    @Override
    @Transactional
    public void sendIdentityOtp(BeneficiaryClaimRequest request, UUID currentUserId) {
        Vault vault = loadAccessibleVault(request.getVaultId(), currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);

        if (request.getVerificationMethod() != VerificationMethod.OTP) {
            throw new BeneficiaryException("This verification method does not use OTP.", HttpStatus.BAD_REQUEST);
        }

        IdentityVerification verification =
                findPendingSession(vault.getId(), currentUserId, VerificationMethod.OTP);

        LocalDateTime now = LocalDateTime.now();

        // Không gửi OTP khi phiên không còn đủ thời gian cho một OTP trọn vẹn
        LocalDateTime sessionEnd = verification.getCreatedAt().plusMinutes(VERIFICATION_TTL_MINUTES);
        if (now.plusMinutes(OTP_TTL_MINUTES).isAfter(sessionEnd)) {
            throw new BeneficiaryException(
                    "The verification session is about to expire. Please start the claim request again.", HttpStatus.BAD_REQUEST);
        }

        // Chặn gửi lại quá nhanh, dựa vào thời điểm gửi lần trước được lưu tường minh
        if (verification.getOtpSentAt() != null
                && now.isBefore(verification.getOtpSentAt().plusSeconds(OTP_RESEND_SECONDS))) {
            throw new BeneficiaryException(
                    "Please wait a moment before requesting another OTP.", HttpStatus.TOO_MANY_REQUESTS);
        }

        // Gửi lại KHÔNG reset attemptCount, tránh lách giới hạn số lần thử
        String otp = otpGenerator.generate();
        verification.setOtpCode(otpHasher.hash(verification.getId(), otp)); // DB chỉ giữ hash
        verification.setOtpSentAt(now);
        verification.setOtpExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        verificationRepository.save(verification);

        // OTP gốc chỉ tồn tại trong bộ nhớ và email, không bao giờ ghi DB hay log.
        // FR-17: send after commit so no code goes out for a rolled-back transaction.
        UUID vaultId = vault.getId();
        String recipient = emailOf(vault, currentUserId);
        AfterCommit.run(() -> {
            try {
                emailSender.sendEmail(recipient, otp);
            } catch (Exception e) {
                // Never log the OTP. The user can resend after OTP_RESEND_SECONDS.
                log.error("FR-17: identity OTP mail failed, vaultId={}", vaultId, e);
                auditLogService.log(AuditAction.IDENTITY_OTP_SENT, AuditResult.FAILURE, currentUserId,
                        recipient, "Vault", vaultId.toString(), "mail delivery failed");
            }
        });
        auditLogService.log(AuditAction.IDENTITY_OTP_SENT, AuditResult.SUCCESS, currentUserId,
                emailOf(vault, currentUserId), "Vault", vault.getId().toString(), null);
    }

    @Override
    @Transactional(noRollbackFor = BeneficiaryException.class)
    public VerifyIdentityResponse verifyIdentity(VerifyIdentityRequest request, UUID currentUserId) {
        Vault vault = loadAccessibleVault(request.getVaultId(), currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);

        IdentityVerification verification =
                findPendingSession(vault.getId(), currentUserId, request.getVerificationMethod());

        LocalDateTime now = LocalDateTime.now();
        boolean isOtp = request.getVerificationMethod() == VerificationMethod.OTP;

        // 1. Kiểm tra đầu vào. Các lỗi này không tính vào số lần thử.
        if (isOtp) {
            if (request.getOtp() == null) {
                throw new BeneficiaryException("OTP is required.", HttpStatus.BAD_REQUEST);
            }
            if (verification.getOtpCode() == null || verification.getOtpExpiresAt() == null) {
                throw new BeneficiaryException(
                        "No OTP yet. Please request an OTP first.", HttpStatus.BAD_REQUEST);
            }
            if (now.isAfter(verification.getOtpExpiresAt())) {
                throw new BeneficiaryException(
                        "The OTP has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
            }
        } else {
            if (!mockKycVerifier.isEnabled()) {
                throw new BeneficiaryException("The eKYC method is not supported yet.", HttpStatus.BAD_REQUEST);
            }
            if (request.getKycIdNumber() == null) {
                throw new BeneficiaryException("ID number is required.", HttpStatus.BAD_REQUEST);
            }
        }

        // 2. GIỮ CHỖ một lượt thử TRƯỚC khi chấm đáp án. UPDATE khóa dòng nên các request song song
        //    bị xếp hàng, mỗi request nhận một số đếm khác nhau. Nếu chấm trước rồi mới đếm,
        //    kẻ tấn công vẫn đoán được nhiều lần cùng lúc.
        verificationRepository.incrementAttemptCount(verification.getId());
        int attempts = verificationRepository.findAttemptCountById(verification.getId());
        // Đồng bộ entity với DB, nếu không lần lưu sau sẽ ghi đè bằng số đếm cũ
        verification.setAttemptCount(attempts);
        int totalAttempts = attempts + verificationRepository.sumAttemptsOfOtherPendingSessions(
                vault.getId(), currentUserId, verification.getId(), now.minusHours(24));

        // 3. Hết lượt: không chấm đáp án nữa, kể cả khi đáp án đúng
        if (totalAttempts > MAX_VERIFY_ATTEMPTS) {
            lockSession(verification);
            auditLogService.log(AuditAction.IDENTITY_LOCKED, AuditResult.FAILURE, currentUserId,
                    emailOf(vault, currentUserId), "Vault", vault.getId().toString(), "attempts=" + totalAttempts);
            throw new BeneficiaryException(LOCKED_MSG, HttpStatus.LOCKED);
        }

        // 4. Chấm đáp án
        boolean passed = isOtp
                ? otpHasher.matches(verification.getId(), request.getOtp(), verification.getOtpCode())
                : mockKycVerifier.verify(request.getKycIdNumber());

        if (!passed) {
            auditLogService.log(AuditAction.IDENTITY_VERIFY_FAILED, AuditResult.FAILURE, currentUserId,
                    emailOf(vault, currentUserId), "Vault", vault.getId().toString(),
                    "method=" + request.getVerificationMethod() + ", attempt=" + totalAttempts);
            if (totalAttempts >= MAX_VERIFY_ATTEMPTS) {
                lockSession(verification);
                auditLogService.log(AuditAction.IDENTITY_LOCKED, AuditResult.FAILURE, currentUserId,
                        emailOf(vault, currentUserId), "Vault", vault.getId().toString(), "attempts=" + totalAttempts);
                throw new BeneficiaryException(LOCKED_MSG, HttpStatus.LOCKED);
            }
            throw new BeneficiaryException(
                    "Verification failed. Attempts left: " + (MAX_VERIFY_ATTEMPTS - totalAttempts) + ".",
                    HttpStatus.BAD_REQUEST);
        }

        // 5. Thành công
        verification.setStatus(VerificationStatus.SUCCESS);
        verification.setVerifiedAt(now);
        clearOtp(verification);
        verificationRepository.save(verification);

        auditLogService.log(AuditAction.IDENTITY_VERIFIED, AuditResult.SUCCESS, currentUserId,
                emailOf(vault, currentUserId), "Vault", vault.getId().toString(),
                "method=" + request.getVerificationMethod());

        return VerifyIdentityResponse.builder()
                .verificationId(verification.getId())
                .status(VerificationStatus.SUCCESS)
                .verifiedAt(now)
                .viewSessionExpiresAt(now.plusMinutes(VIEW_SESSION_MINUTES))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InheritedAssetSummaryResponse> getInheritedAssets(UUID vaultId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        // N2-access: bỏ tài sản đã phân bổ cho người khác
        Set<UUID> hidden = hiddenAssetIds(vault.getId(), currentUserId);
        List<DigitalAsset> visible = digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE)
                .stream().filter(asset -> !hidden.contains(asset.getId())).toList();

        // X1: tiến độ đồng thuận của các tài sản JOINT_CONSENSUS (1 query cho cả vault)
        UUID myId = requireBeneficiary(vault.getId(), currentUserId).getId();
        Map<UUID, JointConsensusService.Info> consensus = jointConsensusService.infoByAsset(
                assetAllocationRepository.findByAssetIdIn(visible.stream().map(DigitalAsset::getId).toList()), myId);

        return visible.stream()
                .map(asset -> InheritedAssetSummaryResponse.builder()
                        .id(asset.getId())
                        .assetType(asset.getAssetType())
                        .assetName(asset.getAssetName())
                        .consensus(toConsensusResponse(asset.getId(), consensus.get(asset.getId())))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public InheritedAssetDetailResponse getInheritedAssetDetail(UUID vaultId, UUID assetId, UUID currentUserId) {
        DecryptedAsset decrypted = loadDecryptedAsset(vaultId, assetId, currentUserId);
        DigitalAsset asset = decrypted.asset();

        InheritedAssetDetailResponse response = InheritedAssetDetailResponse.builder()
                .id(asset.getId())
                .assetType(asset.getAssetType())
                .assetName(asset.getAssetName())
                .secret(decrypted.secret())
                .notes(decrypted.notes())
                .build();

        // Chỉ ghi audit khi giải mã thành công, và không đưa dữ liệu rõ vào log
        auditLogService.log(AuditAction.ASSET_VIEWED, AuditResult.SUCCESS, currentUserId,
                emailOf(decrypted.vault(), currentUserId), decrypted.vault().getId(),
                "DigitalAsset", asset.getId().toString(), null);
        return response;
    }

    // FR-18: tải xuống thông tin tài sản đã giải mã dưới dạng tệp .txt
    @Override
    @Transactional
    public AssetDownloadResponse downloadInheritedAsset(UUID vaultId, UUID assetId, UUID currentUserId) {
        // Kiểm tra lại phiên xác thực còn hiệu lực (SRS FR-18 bước 2) nằm trong loadDecryptedAsset
        DecryptedAsset decrypted = loadDecryptedAsset(vaultId, assetId, currentUserId);
        DigitalAsset asset = decrypted.asset();

        String content = buildDownloadContent(decrypted);

        // SRS FR-18 bước 5: ghi log tải xuống, chỉ sau khi giải mã thành công, không đưa dữ liệu rõ vào log
        auditLogService.log(AuditAction.ASSET_DOWNLOADED, AuditResult.SUCCESS, currentUserId,
                emailOf(decrypted.vault(), currentUserId), decrypted.vault().getId(),
                "DigitalAsset", asset.getId().toString(), null);

        return AssetDownloadResponse.builder()
                .fileName(toSafeFileName(asset.getAssetName()) + ".txt")
                .content(content.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    // FR-18 (phần 2): danh sách tài liệu pháp lý của vault, chỉ thông tin tóm tắt
    @Override
    @Transactional(readOnly = true)
    public List<LegalDocumentSummaryResponse> getVaultDocuments(UUID vaultId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        return legalDocumentRepository.findByVaultIdAndIsCurrentTrueOrderByUploadedAtDescIdDesc(vault.getId()).stream()
                .map(doc -> LegalDocumentSummaryResponse.builder()
                        .id(doc.getId())
                        .documentType(doc.getDocumentType())
                        .fileName(doc.getFileName())
                        .uploadedAt(doc.getUploadedAt())
                        .build())
                .toList();
    }

    // FR-18 (phần 2): tải xuống một tài liệu pháp lý đã giải mã
    @Override
    @Transactional(readOnly = true)
    public DocumentDownloadResponse downloadVaultDocument(UUID vaultId, UUID documentId, UUID currentUserId) {
        // Kiểm tra lại phiên xác thực còn hiệu lực (SRS FR-18 bước 2)
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        // Bắt buộc thuộc đúng vault, không thì 404 để không lộ tài liệu của vault khác
        LegalDocument document = legalDocumentRepository.findByIdAndVaultIdAndIsCurrentTrue(documentId, vault.getId())
                .orElseThrow(() -> new BeneficiaryException("Document not found.", HttpStatus.NOT_FOUND));

        byte[] plain;
        try {
            // Đọc bản mã hóa từ đĩa rồi giải mã trong bộ nhớ, không lưu lại bản rõ
            plain = cryptoService.decryptBytes(fileStorageService.readFile(document.getFileUrlEncrypted()));
        } catch (StorageUnavailableException e) {
            // H1: storage mất kết nối -> 503; không ghi audit FAILURE vì đây không phải dấu hiệu tệp bị sửa hay sai khóa
            throw e;
        } catch (Exception e) {
            log.error("Document read/decrypt failed for document {}", document.getId(), e);
            // Giải mã thất bại có thể là dấu hiệu tệp bị sửa hoặc sai khóa, nên cần nằm trong audit
            auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.FAILURE, currentUserId,
                    emailOf(vault, currentUserId), vault.getId(),
                    "LegalDocument", document.getId().toString(), "Read or decrypt failed");
            throw new BeneficiaryException(
                    "Could not decrypt the document. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String fileName = toSafeDocumentFileName(document.getFileName());

        auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.SUCCESS, currentUserId,
                emailOf(vault, currentUserId), vault.getId(),
                "LegalDocument", document.getId().toString(), null);

        return DocumentDownloadResponse.builder()
                .fileName(fileName)
                .contentType(contentTypeOf(fileName))
                .content(plain)
                .build();
    }

    // ===================== FR-19 =====================

    @Override
    @Transactional(readOnly = true)
    public CloseVaultPreviewResponse previewClose(UUID vaultId, UUID currentUserId) {
        AccessContext ctx = loadAccessibleVault(vaultId, currentUserId);
        Vault vault = ctx.vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        // N2-access: chỉ đếm phần của người này
        Set<UUID> hidden = hiddenAssetIds(vault.getId(), currentUserId);
        int total = (int) digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE).stream()
                .filter(a -> !hidden.contains(a.getId()))
                .count();
        List<InheritedAssetSummaryResponse> unviewed = digitalAssetRepository
                .findUnaccessedAssets(vault.getId(), ctx.claim().getId()).stream()
                .filter(a -> !hidden.contains(a.getId()))
                .map(a -> InheritedAssetSummaryResponse.builder()
                        .id(a.getId())
                        .assetType(a.getAssetType())
                        .assetName(a.getAssetName())
                        .build())
                .toList();

        return CloseVaultPreviewResponse.builder()
                .totalAssets(total)
                .viewedCount(total - unviewed.size())
                .unviewedCount(unviewed.size())
                .unviewedAssets(unviewed)
                .build();
    }

    @Override
    @Transactional
    public CloseVaultResponse closeVault(UUID vaultId, CloseVaultRequest request, UUID currentUserId) {
        vaultRepository.findByIdForUpdate(vaultId); // N2b: khóa vault trước claim, cùng thứ tự với ClaimTimeoutScheduler
        AccessContext ctx = loadAccessibleVault(vaultId, currentUserId);
        Vault vault = ctx.vault();
        BeneficiaryClaim claim = ctx.claim();
        assertNotLocked(vault.getId(), currentUserId);

        // SRS FR-19 3a: chưa xác nhận lần cuối thì giữ nguyên trạng thái
        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            throw new BeneficiaryException(CONFIRM_REQUIRED_MSG, HttpStatus.BAD_REQUEST);
        }

        // Đóng hồ sơ không hoàn tác được và khóa nội dung, nên yêu cầu phiên xác thực còn hiệu lực
        assertViewSession(vault.getId(), currentUserId);

        // Sau CLAIMED không giải mã được nữa (Q1), nên tài sản chưa xem sẽ mất quyền xem.
        // N2-access: chỉ tính phần của người này (tài sản của người khác không bắt họ xem, cũng không cảnh báo "chưa xem")
        Set<UUID> hidden = hiddenAssetIds(vault.getId(), currentUserId);
        List<DigitalAsset> unviewed = digitalAssetRepository.findUnaccessedAssets(vault.getId(), claim.getId()).stream()
                .filter(a -> !hidden.contains(a.getId()))
                .toList();
        // FR-19 (SRS Precondition): Vault có tài sản thì Beneficiary phải xem/tải ít nhất một tài sản trước khi đóng
        long totalAssets = digitalAssetRepository.countByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE)
                - hidden.size();
        if (totalAssets > 0 && unviewed.size() == totalAssets) {
            throw new BeneficiaryException(
                    "You must view or download at least one asset before closing the case.", HttpStatus.BAD_REQUEST);
        }
        // Cho đóng khi còn tài sản chưa xem, nhưng bắt buộc xác nhận rõ ràng.
        if (!unviewed.isEmpty() && !Boolean.TRUE.equals(request.getAcknowledgeUnviewed())) {
            throw new BeneficiaryException("Unviewed assets: " + unviewed.size()
                    + ". After closing the case you will no longer be able to view them. "
                    + "If you still want to close it, send acknowledgeUnviewed = true.", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        // Chuyển trạng thái nguyên tử: request đóng song song thứ hai nhận 409, không ghi audit trùng
        if (claimRepository.markClaimed(claim.getId(), now) == 0) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        claim.setStatus(ClaimStatus.CLAIMED);
        claim.setClaimedAt(now);
        // N2b: chỉ đóng phần của người này; vault sang CLAIMED khi mọi phần đã xong
        completeVaultIfAllDone(vault);

        // Ghi số lượng và id tài sản chưa xem để đối soát sau này, không có dữ liệu rõ
        String detail = "unviewedCount=" + unviewed.size()
                + ", unviewedAssetIds=" + unviewed.stream().map(a -> a.getId().toString()).toList();
        auditLogService.log(AuditAction.CLAIM_COMPLETED, AuditResult.SUCCESS, currentUserId,
                emailOf(vault, currentUserId), "Vault", vault.getId().toString(), detail);

        return CloseVaultResponse.builder()
                .vaultId(vault.getId())
                .vaultStatus(vault.getStatus())
                .claimStatus(claim.getStatus())
                .claimedAt(claim.getClaimedAt())
                .build();
    }

    // Kết quả đã giải mã, dùng chung cho xem chi tiết (FR-17) và tải xuống (FR-18)
    private record DecryptedAsset(Vault vault, DigitalAsset asset, String secret, String notes) {
    }

    private record AccessContext(Vault vault, BeneficiaryClaim claim) {
    }

    // Kiểm tra quyền, trạng thái, khóa, phiên xem rồi giải mã trong bộ nhớ
    private DecryptedAsset loadDecryptedAsset(UUID vaultId, UUID assetId, UUID currentUserId) {
        AccessContext ctx = loadAccessibleVault(vaultId, currentUserId);
        Vault vault = ctx.vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        DigitalAsset asset = digitalAssetRepository
                .findByIdAndVaultIdAndStatus(assetId, vault.getId(), AssetStatus.ACTIVE)
                .orElseThrow(() -> new BeneficiaryException("Asset not found.", HttpStatus.NOT_FOUND));

        // N2-access: chặn trước khi giải mã và trước khi ghi nhận "đã xem" (dùng chung cho xem chi tiết và tải xuống)
        if (hiddenAssetIds(vault.getId(), currentUserId).contains(asset.getId())) {
            throw new BeneficiaryException(ErrorCode.ASSET_NOT_ALLOCATED_TO_YOU,
                    "This asset is not allocated to you.");
        }

        // X1: tài sản JOINT_CONSENSUS chỉ mở khi đủ K người đồng ý (chặn trước khi giải mã)
        jointConsensusService.assertReached(asset.getId());

        DecryptedAsset decrypted;
        try {
            // Giải mã trong bộ nhớ cho phiên xem, không lưu lại bản rõ
            decrypted = new DecryptedAsset(vault, asset,
                    cryptoService.decrypt(asset.getEncryptedSecret()),
                    cryptoService.decrypt(asset.getNotesEncrypted()));
        } catch (Exception e) {
            // Không log dữ liệu đã giải mã, chỉ log id tài sản
            log.error("Decrypt failed for asset {}", asset.getId(), e);
            throw new BeneficiaryException(
                    "Could not decrypt the asset. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Chỉ ghi nhận khi giải mã thành công (điều kiện đóng hồ sơ ở FR-19)
        markContentAccessed(ctx.claim().getId(), asset.getId());
        return decrypted;
    }

    // ===================== X1: đồng thuận K-of-N =====================

    @Override
    @Transactional(readOnly = true)
    public ConsensusStatusResponse getConsensusStatus(UUID vaultId, UUID assetId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);
        DigitalAsset asset = requireVisibleAsset(vault, assetId, currentUserId);
        VaultBeneficiary me = requireBeneficiary(vault.getId(), currentUserId);

        JointConsensusService.Info info = jointConsensusService.infoOf(
                assetAllocationRepository.findByAssetId(asset.getId()), me.getId());
        if (!info.joint()) {
            throw new BeneficiaryException(ErrorCode.CONSENSUS_NOT_APPLICABLE,
                    "This asset does not require joint consensus.");
        }
        return toConsensusResponse(asset.getId(), info);
    }

    @Override
    @Transactional
    public ConsensusStatusResponse approveConsensus(UUID vaultId, UUID assetId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);
        DigitalAsset asset = requireVisibleAsset(vault, assetId, currentUserId);
        VaultBeneficiary me = requireBeneficiary(vault.getId(), currentUserId);

        boolean alreadyApproved = assetAllocationRepository.findByAssetId(asset.getId()).stream()
                .anyMatch(r -> r.getBeneficiary().getId().equals(me.getId()) && r.getConsensusApprovedAt() != null);
        JointConsensusService.Info info = jointConsensusService.approve(asset.getId(), me);

        if (!alreadyApproved) {
            auditLogService.log(AuditAction.ASSET_CONSENSUS_APPROVED, AuditResult.SUCCESS, currentUserId,
                    emailOf(vault, currentUserId), vault.getId(), "DigitalAsset", asset.getId().toString(),
                    "approved=" + info.approved() + "/" + info.threshold() + ", reached=" + info.reached());
        }
        return toConsensusResponse(asset.getId(), info);
    }

    // Tài sản ACTIVE của vault và đã phân bổ cho người này (không thì 404 / ASSET_NOT_ALLOCATED_TO_YOU)
    private DigitalAsset requireVisibleAsset(Vault vault, UUID assetId, UUID currentUserId) {
        DigitalAsset asset = digitalAssetRepository
                .findByIdAndVaultIdAndStatus(assetId, vault.getId(), AssetStatus.ACTIVE)
                .orElseThrow(() -> new BeneficiaryException("Asset not found.", HttpStatus.NOT_FOUND));
        if (hiddenAssetIds(vault.getId(), currentUserId).contains(asset.getId())) {
            throw new BeneficiaryException(ErrorCode.ASSET_NOT_ALLOCATED_TO_YOU,
                    "This asset is not allocated to you.");
        }
        return asset;
    }

    private static ConsensusStatusResponse toConsensusResponse(UUID assetId, JointConsensusService.Info info) {
        if (info == null || !info.joint()) {
            return null;
        }
        return ConsensusStatusResponse.builder()
                .assetId(assetId)
                .threshold(info.threshold())
                .total(info.total())
                .approved(info.approved())
                .myApproved(info.myApproved())
                .reached(info.reached())
                .build();
    }

    // FR-19: ghi nhận LẦN ĐẦU Beneficiary xem chi tiết hoặc tải xuống nội dung của từng tài sản
    private void markContentAccessed(UUID claimId, UUID assetId) {
        assetAccessRecordRepository.insertIfAbsent(UUID.randomUUID(), claimId, assetId, LocalDateTime.now());
    }

    // N2-access: id các tài sản ACTIVE đã phân bổ cho người khác (người này không được xem/tải/đếm).
    // Tính theo effective_beneficiary nên tự đúng sau fallback của N2b. Tài sản chưa có phân bổ thì không bị ẩn (Q47 b).
    private Set<UUID> hiddenAssetIds(UUID vaultId, UUID currentUserId) {
        VaultBeneficiary me = requireBeneficiary(vaultId, currentUserId);
        return new HashSet<>(assetAllocationRepository.findAssetIdsNotHeldBy(vaultId, me.getId()));
    }

    private String buildDownloadContent(DecryptedAsset decrypted) {
        DigitalAsset asset = decrypted.asset();
        String notes = decrypted.notes() == null || decrypted.notes().isBlank()
                ? "(none)" : decrypted.notes();

        return "LegacyVault - Inherited asset information\n"
                + "========================================\n"
                + "Asset name: " + asset.getAssetName() + "\n"
                + "Asset type: " + asset.getAssetType() + "\n"
                + "Access information: " + decrypted.secret() + "\n"
                + "Note: " + notes + "\n"
                + "Exported at: " + ZonedDateTime.now(VN_ZONE).format(EXPORT_TIME_FORMAT) + "\n"
                + "\n"
                + "Please keep this information safe and delete the file after saving it.\n";
    }

    // Tên tài sản do người dùng nhập nên phải làm sạch trước khi đặt tên tệp (chống header injection, ký tự cấm)
    private String toSafeFileName(String assetName) {
        return toSafeFileName(assetName, "asset");
    }

    private String toSafeFileName(String name, String fallback) {
        String base = (name == null ? "" : name)
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]", "_")
                .trim()
                .replaceAll("[. ]+$", "");
        if (base.isEmpty()) {
            return fallback;
        }
        // Cắt tối đa 80 ký tự theo code point để không làm hỏng ký tự đặc biệt
        return base.codePoints()
                .limit(80)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    // Tên tệp tài liệu do Owner đặt: làm sạch phần tên, giữ lại phần mở rộng ngắn hợp lệ để mở đúng ứng dụng
    private String toSafeDocumentFileName(String originalName) {
        String name = originalName == null ? "" : originalName;
        int dot = name.lastIndexOf('.');
        String candidate = (dot > 0 && dot < name.length() - 1)
                ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";

        String base;
        String ext = "";
        if (DOCUMENT_TYPES.containsKey(candidate)) {
            ext = candidate;
            base = name.substring(0, dot);
        } else {
            // Phần mở rộng không nằm trong danh sách cho phép: bỏ dấu chấm để tên không còn đuôi (x.exe -> x_exe)
            base = name.replace('.', '_');
        }
        String safeBase = toSafeFileName(base, "document");
        return ext.isEmpty() ? safeBase : safeBase + "." + ext;
    }

    private String contentTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return DOCUMENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    // ===================== N2b: Decline + auto-fallback =====================

    @Override
    @Transactional
    public AcceptClaimResponse acceptClaim(UUID vaultId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        // Chỉ nhận được khi đã xác minh danh tính (phiên xem còn hiệu lực)
        assertViewSession(vault.getId(), currentUserId);

        VaultBeneficiary me = requireBeneficiary(vaultId, currentUserId);
        if (me.getStatus() != BeneficiaryInviteStatus.ACCEPTED) {
            me.setStatus(BeneficiaryInviteStatus.ACCEPTED);
            me.setRespondedAt(LocalDateTime.now());
            vaultBeneficiaryRepository.save(me);
            auditLogService.log(AuditAction.CLAIM_ACCEPTED, AuditResult.SUCCESS, currentUserId, me.getEmail(),
                    "Vault", vaultId.toString(), null);
        }
        return AcceptClaimResponse.builder().vaultId(vaultId).status(me.getStatus()).build();
    }

    @Override
    @Transactional
    public DeclineClaimResponse declineClaim(UUID vaultId, UUID currentUserId, String reason) {
        // N2b: khóa vault trước (vault -> claim) để không bỏ sót việc hoàn tất
        Vault vault = vaultRepository.findByIdForUpdate(vaultId)
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));
        VaultBeneficiary me = requireBeneficiary(vaultId, currentUserId);
        // Mỗi người chỉ phản hồi một lần: đã Accept thì không Decline được nữa
        if (me.getStatus() == BeneficiaryInviteStatus.ACCEPTED) {
            throw new BeneficiaryException(ErrorCode.CLAIM_ALREADY_RESPONDED,
                    "You have already accepted this inheritance.");
        }

        if (vault.getStatus() == VaultStatus.CLAIMED) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BeneficiaryException(CLAIM_ENDED_MSG, HttpStatus.GONE);
        }
        if (vault.getStatus() != VaultStatus.UNLOCKED) {
            throw new BeneficiaryException(NOT_READY_MSG, HttpStatus.BAD_REQUEST);
        }

        // Từ chối không đảo ngược được: cùng cửa xác minh danh tính với Accept
        assertNotLocked(vaultId, currentUserId);
        assertViewSession(vaultId, currentUserId);

        LocalDateTime now = LocalDateTime.now();
        // Đã qua xác minh thì claim chắc chắn tồn tại (initializeClaim tạo ra)
        BeneficiaryClaim claim = claimRepository.findByVaultIdAndBeneficiaryId(vaultId, currentUserId)
                .orElseThrow(() -> new BeneficiaryException(NOT_READY_MSG, HttpStatus.BAD_REQUEST));
        if (claim.getStatus() == ClaimStatus.CLAIMED) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        if (claim.getStatus() == ClaimStatus.EXPIRED || now.isAfter(claim.getClaimDeadlineAt())) {
            throw new BeneficiaryException(CLAIM_ENDED_MSG, HttpStatus.GONE);
        }
        claim.setStatus(ClaimStatus.DECLINED);
        claimRepository.save(claim);

        me.setStatus(BeneficiaryInviteStatus.DECLINED);
        me.setRespondedAt(now);
        vaultBeneficiaryRepository.save(me);

        // Auto-fallback: chuyển từng phân bổ đang giữ sang người dự phòng còn nhận được
        int reassigned = 0;
        int stalled = 0;
        for (AssetAllocation allocation : assetAllocationRepository.findHeldBy(vaultId, me.getId())) {
            VaultBeneficiary next = allocation.getContingentBeneficiary();
            if (next != null && !next.getId().equals(me.getId()) && canReceive(vaultId, next)) {
                allocation.setEffectiveBeneficiary(next);
                assetAllocationRepository.save(allocation);
                reassigned++;
                auditLogService.log(AuditAction.ASSET_ALLOCATION_FALLBACK, AuditResult.SUCCESS, currentUserId,
                        me.getEmail(), vaultId, "Asset", allocation.getAsset().getId().toString(),
                        "from=" + me.getId() + ", to=" + next.getId());
            } else {
                stalled++;
            }
        }

        auditLogService.log(AuditAction.CLAIM_DECLINED, AuditResult.SUCCESS, currentUserId, me.getEmail(),
                "Vault", vaultId.toString(), "reassigned=" + reassigned + ", stalled=" + stalled
                        + (reason != null && !reason.isBlank() ? ", reason=" + reason.trim() : ""));

        // Không còn tài sản nào có người nhận, hoặc cả vault không còn ai nhận: báo để Admin/Executor xử lý
        boolean anyoneLeft = vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId).stream()
                .anyMatch(b -> b.getStatus() != BeneficiaryInviteStatus.DECLINED
                        && b.getStatus() != BeneficiaryInviteStatus.WAIVED);
        if (stalled > 0 || !anyoneLeft) {
            auditLogService.log(AuditAction.CLAIM_STALLED, AuditResult.FAILURE, currentUserId, me.getEmail(),
                    "Vault", vaultId.toString(), "stalledAssets=" + stalled + ", anyoneLeft=" + anyoneLeft);
        }

        // Nếu mọi người còn lại đã đóng phần của mình thì vault hoàn tất (chỉ khi không còn tài sản stalled)
        completeVaultIfAllDone(vault);

        return DeclineClaimResponse.builder()
                .vaultId(vaultId)
                .claimStatus(claim.getStatus())
                .reassignedAssets(reassigned)
                .stalledAssets(stalled)
                .build();
    }

    // ===================== Helper dùng chung cho FR-17 =====================

    // N2b: cổng truy cập theo vault_beneficiaries. Người lạ trả 404 để không lộ vault;
    // người đã từ chối / từ bỏ trả 409.
    private VaultBeneficiary requireBeneficiary(UUID vaultId, UUID currentUserId) {
        VaultBeneficiary vb = vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, currentUserId)
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));
        if (vb.getStatus() == BeneficiaryInviteStatus.DECLINED || vb.getStatus() == BeneficiaryInviteStatus.WAIVED) {
            throw new BeneficiaryException(ErrorCode.CLAIM_ALREADY_RESPONDED, "You have declined this inheritance.");
        }
        return vb;
    }

    // Email dùng cho audit; thay cho vault.getBeneficiary().getEmail() vì giờ vault có nhiều người thụ hưởng
    private String emailOf(Vault vault, UUID userId) {
        return vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), userId)
                .map(VaultBeneficiary::getEmail)
                .orElse(null);
    }

    // N2b: vault chỉ sang CLAIMED khi mọi người thụ hưởng còn nhận được đều đã đóng phần của mình.
    // Nếu không ai đã đóng (vd. tất cả từ chối) thì giữ nguyên UNLOCKED đến hết hạn nhận.
    private void completeVaultIfAllDone(Vault vault) {
        // Q49: còn phân bổ không có người nhận thì giữ UNLOCKED, chờ Admin xử lý hoặc hết hạn BR-04
        if (assetAllocationRepository.countStalledInVault(vault.getId(),
                List.of(BeneficiaryInviteStatus.DECLINED, BeneficiaryInviteStatus.WAIVED)) > 0) {
            return;
        }
        List<UUID> claimedUserIds = claimRepository.findAllByVaultId(vault.getId()).stream()
                .filter(c -> c.getStatus() == ClaimStatus.CLAIMED)
                .map(c -> c.getBeneficiary().getId())
                .toList();
        if (claimedUserIds.isEmpty()) {
            return;
        }
        boolean allDone = vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vault.getId()).stream()
                .filter(b -> b.getStatus() != BeneficiaryInviteStatus.DECLINED
                        && b.getStatus() != BeneficiaryInviteStatus.WAIVED)
                .allMatch(b -> b.getUser() != null && claimedUserIds.contains(b.getUser().getId()));
        if (allDone) {
            vault.setStatus(VaultStatus.CLAIMED);
            vaultRepository.save(vault);
        }
    }

    // N2b: người dự phòng chỉ nhận được phần chuyển sang nếu chưa đóng / chưa hết hạn / chưa từ chối
    private boolean canReceive(UUID vaultId, VaultBeneficiary vb) {
        if (vb.getStatus() == BeneficiaryInviteStatus.DECLINED || vb.getStatus() == BeneficiaryInviteStatus.WAIVED) {
            return false;
        }
        if (vb.getUser() == null) {
            return true; // chưa đăng ký: sẽ có claim khi đăng ký xong
        }
        return claimRepository.findByVaultIdAndBeneficiaryId(vaultId, vb.getUser().getId())
                .map(c -> c.getStatus() == ClaimStatus.PENDING)
                .orElse(true);
    }

    // Kiểm tra quyền và trạng thái dùng chung cho FR-17 (cùng quy tắc với FR-16)
    private AccessContext loadAccessibleVault(UUID vaultId, UUID currentUserId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));

        requireBeneficiary(vault.getId(), currentUserId);

        if (vault.getStatus() == VaultStatus.CLAIMED) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BeneficiaryException(CLAIM_ENDED_MSG, HttpStatus.GONE);
        }
        if (vault.getStatus() != VaultStatus.UNLOCKED) {
            throw new BeneficiaryException(NOT_READY_MSG, HttpStatus.BAD_REQUEST);
        }

        BeneficiaryClaim claim = claimRepository.findByVaultIdAndBeneficiaryId(vault.getId(), currentUserId)
                .orElseThrow(() -> new BeneficiaryException(NO_SESSION_MSG, HttpStatus.BAD_REQUEST));

        if (claim.getStatus() == ClaimStatus.CLAIMED) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        if (claim.getStatus() == ClaimStatus.EXPIRED || LocalDateTime.now().isAfter(claim.getClaimDeadlineAt())) {
            throw new BeneficiaryException(CLAIM_ENDED_MSG, HttpStatus.GONE);
        }
        return new AccessContext(vault, claim);
    }

    @Override
    @Transactional
    public InheritanceDossierData getDossierData(UUID vaultId, UUID currentUserId) {
        // Dùng lại đúng các bước kiểm tra của getInheritedAssets
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);
        VaultBeneficiary me = requireBeneficiary(vault.getId(), currentUserId);

        // 2. Chỉ tài sản người này đang giữ (người chính, hoặc nhận thay do người chính từ chối)
        List<InheritanceDossierData.AssetLine> assets = assetAllocationRepository.findHeldBy(vault.getId(), me.getId()).stream()
                .filter(al -> al.getAsset().getStatus() == AssetStatus.ACTIVE)
                .map(al -> new InheritanceDossierData.AssetLine(
                        al.getAsset().getAssetName(),
                        al.getAsset().getAssetType(),
                        TransferMechanisms.resolve(al.getAsset().getTransferMechanism(), al.getAsset().getAssetType()),
                        al.getMode(),
                        al.getSharePercent(),
                        al.getNote(),
                        !al.getBeneficiary().getId().equals(me.getId()),
                        dossierFields(al.getAsset())))
                .toList();
        // X5: chỉ lấy các bản tài liệu hiện hành (is_current = true)
        List<InheritanceDossierData.DocumentLine> documents = legalDocumentRepository.findByVaultIdAndIsCurrentTrueOrderByUploadedAtDescIdDesc(vault.getId()).stream()
                .map(d -> new InheritanceDossierData.DocumentLine(d.getFileName(), d.getDocumentType(), d.getUploadedAt()))
                .toList();

        // 1. Chữ ký Verifier + người thi hành của hồ sơ đã ký; chưa ký thì lấy Executor đang ACTIVE
        Optional<DigitalSignature> sig = digitalSignatureRepository
                .findSignedForVault(vault.getId(), PageRequest.of(0, 1)).stream().findFirst();
        InheritanceDossierData.Signature signature = sig.map(s -> new InheritanceDossierData.Signature(
                s.getVerifier().getFullName(), s.getVerifier().getEmail(),
                String.valueOf(s.getSignatureMethod()), s.getSignatureHash(), s.getSignedAt(),
                s.getRequest().getId())).orElse(null);
        User executorUser = sig.map(s -> s.getRequest().getExecutor())
                .or(() -> executorAssignmentRepository.findActiveWithUserByOwnerIdIn(List.of(vault.getOwner().getId()))
                        .stream().findFirst().map(ExecutorAssignment::getExecutorUser))
                .orElse(null);

        User owner = vault.getOwner();
        auditLogService.log(AuditAction.DOSSIER_DOWNLOADED, AuditResult.SUCCESS, currentUserId, null,
                vault.getId(), "Vault", vault.getId().toString(), "assets=" + assets.size());

        return new InheritanceDossierData(
                vault.getId(), vault.getName(), vault.getUnlockedAt(), vault.getClaimDeadlineAt(),
                new InheritanceDossierData.Person(owner.getFullName(), owner.getEmail()),
                new InheritanceDossierData.Person(me.getFullName(), me.getEmail()),
                executorUser == null ? null : new InheritanceDossierData.Person(executorUser.getFullName(), executorUser.getEmail()),
                signature, assets, documents, LocalDateTime.now());
    }

    // FR-17 3b: khóa tạm theo (vault, beneficiary). Có phiên FAILED tạo trong LOCK_HOURS gần nhất thì không tạo
    // phiên mới để lách; phiên FAILED cũ hơn mốc đó không còn khóa nữa.
    private void assertNotLocked(UUID vaultId, UUID currentUserId) {
        if (verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndCreatedAtAfter(
                vaultId, currentUserId, VerificationStatus.FAILED, LocalDateTime.now().minusHours(LOCK_HOURS))) {
            throw new BeneficiaryException(LOCKED_MSG, HttpStatus.LOCKED);
        }
    }

    private IdentityVerification findPendingSession(UUID vaultId, UUID currentUserId, VerificationMethod method) {
        IdentityVerification v = verificationRepository
                .findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusOrderByCreatedAtDesc(
                        vaultId, currentUserId, method, VerificationStatus.PENDING)
                .orElseThrow(() -> new BeneficiaryException(NO_SESSION_MSG, HttpStatus.BAD_REQUEST));
        if (v.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(VERIFICATION_TTL_MINUTES))) {
            throw new BeneficiaryException(NO_SESSION_MSG, HttpStatus.BAD_REQUEST);
        }
        return v;
    }

    // Phiên xem hợp lệ = có phiên SUCCESS với verifiedAt trong VIEW_SESSION_MINUTES gần nhất
    private void assertViewSession(UUID vaultId, UUID currentUserId) {
        boolean valid = verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                vaultId, currentUserId, VerificationStatus.SUCCESS,
                LocalDateTime.now().minusMinutes(VIEW_SESSION_MINUTES));
        if (!valid) {
            throw new BeneficiaryException(ErrorCode.IDENTITY_NOT_VERIFIED,
                    "Identity verification is required before viewing assets.");
        }
    }

    private void lockSession(IdentityVerification verification) {
        verification.setStatus(VerificationStatus.FAILED);
        clearOtp(verification);
        verificationRepository.save(verification);
    }

    private void clearOtp(IdentityVerification verification) {
        verification.setOtpCode(null);
        verification.setOtpSentAt(null);
        verification.setOtpExpiresAt(null);
    }

    private static Map<String, String> dossierFields(DigitalAsset asset) {
        if (asset.getPublicFieldsJson() == null) return Map.of();
        Map<String, String> raw = FlatJson.decode(asset.getPublicFieldsJson());
        AssetSchema schema = AssetSchemaRegistry.schemaOf(asset.getAssetType());
        Map<String, String> out = new LinkedHashMap<>();
        if (schema != null) {
            for (AssetFieldSpec f : schema.fields()) {
                String v = raw.get(f.key());
                if (f.sensitive() || v == null || v.isBlank()) continue;
                out.put(f.key(), mask(f.key(), v));
            }
        }
        return out;
    }

    private static String mask(String key, String v) {
        return DOSSIER_MASKED_KEYS.contains(key) && v.length() > 4 ? "****" + v.substring(v.length() - 4) : v;
    }
}
