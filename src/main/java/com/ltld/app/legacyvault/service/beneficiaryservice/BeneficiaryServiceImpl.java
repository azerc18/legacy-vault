package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.AssetDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryVaultSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultPreviewResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.LegalDocumentSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.AssetAccessRecordRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.MockKycVerifier;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final int VERIFICATION_TTL_MINUTES = 15;
    private static final String NOT_FOUND_MSG = "Không tìm thấy yêu cầu nhận tài sản.";
    private static final int CLAIM_TIMEOUT_DAYS = 60;

    private static final int OTP_TTL_MINUTES = 5;
    private static final int OTP_RESEND_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int VIEW_SESSION_MINUTES = 30;

    private static final String ALREADY_CLAIMED_MSG = "Tài sản này đã được nhận.";
    private static final String CLAIM_ENDED_MSG = "Thời hạn yêu cầu nhận tài sản đã kết thúc.";
    private static final String NOT_READY_MSG = "Tài sản chưa sẵn sàng để nhận.";
    private static final String NO_SESSION_MSG = "Chưa có phiên xác thực. Vui lòng khởi tạo yêu cầu nhận tài sản trước.";
    private static final String LOCKED_MSG = "Truy cập đã bị tạm khóa do xác thực sai nhiều lần. Vui lòng liên hệ Admin để được hỗ trợ.";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss '(GMT+7)'");
    private static final String CONFIRM_REQUIRED_MSG = "Cần xác nhận lần cuối để đóng hồ sơ nhận bàn giao.";
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
        List<Vault> vaults = vaultRepository.findByBeneficiaryIdAndStatusInOrderByUnlockedAtDescNameAscIdAsc(
                currentUserId, List.of(VaultStatus.UNLOCKED, VaultStatus.CLAIMED, VaultStatus.ARCHIVED_LOCKED));
        if (vaults.isEmpty()) {
            return List.of();
        }

        Map<UUID, BeneficiaryClaim> claimsByVaultId = claimRepository
                .findByVaultIdIn(vaults.stream().map(Vault::getId).toList()).stream()
                .collect(Collectors.toMap(c -> c.getVault().getId(), c -> c));

        return vaults.stream().map(v -> {
            BeneficiaryClaim claim = claimsByVaultId.get(v.getId());
            return BeneficiaryVaultSummaryResponse.builder()
                    .vaultId(v.getId())
                    .vaultName(v.getName())
                    .ownerName(v.getOwner() != null ? v.getOwner().getFullName() : null)
                    .vaultStatus(v.getStatus())
                    .claimStatus(claim != null ? claim.getStatus() : null)
                    .unlockedAt(v.getUnlockedAt())
                    .claimDeadlineAt(v.getClaimDeadlineAt())
                    .claimedAt(claim != null ? claim.getClaimedAt() : null)
                    .build();
        }).toList();
    }


    @Override
    @Transactional
    public BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request, UUID currentUserId) {

        Vault vault = vaultRepository.findById(request.getVaultId())
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));

        User beneficiary = vault.getBeneficiary();
        if (beneficiary == null || !beneficiary.getId().equals(currentUserId)) {
            throw new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND);
        }

        if (vault.getStatus() == VaultStatus.CLAIMED) {
            throw new BeneficiaryException("Tài sản này đã được nhận.", HttpStatus.CONFLICT);
        }
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BeneficiaryException("Thời hạn yêu cầu nhận tài sản đã kết thúc.", HttpStatus.GONE);
        }
        if (vault.getStatus() != VaultStatus.UNLOCKED) {
            throw new BeneficiaryException("Tài sản chưa sẵn sàng để nhận.", HttpStatus.BAD_REQUEST);
        }

        BeneficiaryClaim claim = claimRepository.findByVaultId(vault.getId()).orElse(null);

        if (claim != null) {
            if (claim.getStatus() == ClaimStatus.CLAIMED) {
                throw new BeneficiaryException("Tài sản này đã được nhận.", HttpStatus.CONFLICT);
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
            throw new BeneficiaryException("Thời hạn yêu cầu nhận tài sản đã kết thúc.", HttpStatus.GONE);
        }

        if (request.getVerificationMethod() == VerificationMethod.EKYC_MOCK && !mockKycVerifier.isEnabled()) {
            throw new BeneficiaryException("Phương thức eKYC hiện chưa được hỗ trợ.", HttpStatus.BAD_REQUEST);
        }

        IdentityVerification verification = verificationRepository
                .findFirstByVaultIdAndBeneficiaryIdAndMethodAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        vault.getId(), currentUserId, request.getVerificationMethod(),
                        VerificationStatus.PENDING,
                        LocalDateTime.now().minusMinutes(VERIFICATION_TTL_MINUTES))
                .orElseGet(() -> verificationRepository.save(
                        IdentityVerification.builder()
                                .beneficiary(beneficiary)
                                .vault(vault)
                                .method(request.getVerificationMethod())
                                .status(VerificationStatus.PENDING)
                                .build()));

        return BeneficiaryClaimResponse.builder()
                .claimId(claim.getId())
                .vaultId(vault.getId())
                .verificationId(verification.getId())
                .status(claim.getStatus())
                .claimDeadlineAt(claim.getClaimDeadlineAt())
                .claimedAt(claim.getClaimedAt())
                .build();
    }


    @Override
    @Transactional
    public void sendIdentityOtp(BeneficiaryClaimRequest request, UUID currentUserId) {
        Vault vault = loadAccessibleVault(request.getVaultId(), currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);

        if (request.getVerificationMethod() != VerificationMethod.OTP) {
            throw new BeneficiaryException("Phương thức xác thực này không dùng OTP.", HttpStatus.BAD_REQUEST);
        }

        IdentityVerification verification =
                findPendingSession(vault.getId(), currentUserId, VerificationMethod.OTP);

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime sessionEnd = verification.getCreatedAt().plusMinutes(VERIFICATION_TTL_MINUTES);
        if (now.plusMinutes(OTP_TTL_MINUTES).isAfter(sessionEnd)) {
            throw new BeneficiaryException(
                    "Phiên xác thực sắp hết hạn. Vui lòng khởi tạo lại yêu cầu nhận tài sản.", HttpStatus.BAD_REQUEST);
        }

        if (verification.getOtpSentAt() != null
                && now.isBefore(verification.getOtpSentAt().plusSeconds(OTP_RESEND_SECONDS))) {
            throw new BeneficiaryException(
                    "Vui lòng đợi một lúc trước khi yêu cầu gửi lại OTP.", HttpStatus.TOO_MANY_REQUESTS);
        }

        String otp = otpGenerator.generate();
        verification.setOtpCode(otpHasher.hash(verification.getId(), otp)); // DB chỉ giữ hash
        verification.setOtpSentAt(now);
        verification.setOtpExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        verificationRepository.save(verification);

        emailSender.sendEmail(vault.getBeneficiary().getEmail(), otp);
        auditLogService.log(AuditAction.IDENTITY_OTP_SENT, AuditResult.SUCCESS, currentUserId,
                vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(), null);
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

        if (isOtp) {
            if (request.getOtp() == null) {
                throw new BeneficiaryException("OTP không được để trống.", HttpStatus.BAD_REQUEST);
            }
            if (verification.getOtpCode() == null || verification.getOtpExpiresAt() == null) {
                throw new BeneficiaryException(
                        "Chưa có OTP. Vui lòng yêu cầu gửi OTP trước.", HttpStatus.BAD_REQUEST);
            }
            if (now.isAfter(verification.getOtpExpiresAt())) {
                throw new BeneficiaryException(
                        "OTP đã hết hạn. Vui lòng yêu cầu gửi lại OTP.", HttpStatus.BAD_REQUEST);
            }
        } else {
            if (!mockKycVerifier.isEnabled()) {
                throw new BeneficiaryException("Phương thức eKYC hiện chưa được hỗ trợ.", HttpStatus.BAD_REQUEST);
            }
            if (request.getKycIdNumber() == null) {
                throw new BeneficiaryException("Số CCCD không được để trống.", HttpStatus.BAD_REQUEST);
            }
        }

        verificationRepository.incrementAttemptCount(verification.getId());
        int attempts = verificationRepository.findAttemptCountById(verification.getId());
        verification.setAttemptCount(attempts);
        int totalAttempts = attempts + verificationRepository.sumAttemptsOfOtherPendingSessions(
                vault.getId(), currentUserId, verification.getId(), now.minusHours(24));

        if (totalAttempts > MAX_VERIFY_ATTEMPTS) {
            lockSession(verification);
            auditLogService.log(AuditAction.IDENTITY_LOCKED, AuditResult.FAILURE, currentUserId,
                    vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(), "attempts=" + totalAttempts);
            throw new BeneficiaryException(LOCKED_MSG, HttpStatus.LOCKED);
        }

        boolean passed = isOtp
                ? otpHasher.matches(verification.getId(), request.getOtp(), verification.getOtpCode())
                : mockKycVerifier.verify(request.getKycIdNumber());

        if (!passed) {
            auditLogService.log(AuditAction.IDENTITY_VERIFY_FAILED, AuditResult.FAILURE, currentUserId,
                    vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(),
                    "method=" + request.getVerificationMethod() + ", attempt=" + totalAttempts);
            if (totalAttempts >= MAX_VERIFY_ATTEMPTS) {
                lockSession(verification);
                auditLogService.log(AuditAction.IDENTITY_LOCKED, AuditResult.FAILURE, currentUserId,
                        vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(), "attempts=" + totalAttempts);
                throw new BeneficiaryException(LOCKED_MSG, HttpStatus.LOCKED);
            }
            throw new BeneficiaryException(
                    "Xác thực không thành công. Bạn còn " + (MAX_VERIFY_ATTEMPTS - totalAttempts) + " lần thử.",
                    HttpStatus.BAD_REQUEST);
        }

        verification.setStatus(VerificationStatus.SUCCESS);
        verification.setVerifiedAt(now);
        clearOtp(verification);
        verificationRepository.save(verification);

        auditLogService.log(AuditAction.IDENTITY_VERIFIED, AuditResult.SUCCESS, currentUserId,
                vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(),
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

        return digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE).stream()
                .map(asset -> InheritedAssetSummaryResponse.builder()
                        .id(asset.getId())
                        .assetType(asset.getAssetType())
                        .assetName(asset.getAssetName())
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

        auditLogService.log(AuditAction.ASSET_VIEWED, AuditResult.SUCCESS, currentUserId,
                decrypted.vault().getBeneficiary().getEmail(), decrypted.vault().getId(),
                "DigitalAsset", asset.getId().toString(), null);
        return response;
    }

    @Override
    @Transactional
    public AssetDownloadResponse downloadInheritedAsset(UUID vaultId, UUID assetId, UUID currentUserId) {
        DecryptedAsset decrypted = loadDecryptedAsset(vaultId, assetId, currentUserId);
        DigitalAsset asset = decrypted.asset();

        String content = buildDownloadContent(decrypted);

        auditLogService.log(AuditAction.ASSET_DOWNLOADED, AuditResult.SUCCESS, currentUserId,
                decrypted.vault().getBeneficiary().getEmail(), decrypted.vault().getId(),
                "DigitalAsset", asset.getId().toString(), null);

        return AssetDownloadResponse.builder()
                .fileName(toSafeFileName(asset.getAssetName()) + ".txt")
                .content(content.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalDocumentSummaryResponse> getVaultDocuments(UUID vaultId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        return legalDocumentRepository.findByVaultIdOrderByUploadedAtDescIdDesc(vault.getId()).stream()
                .map(doc -> LegalDocumentSummaryResponse.builder()
                        .id(doc.getId())
                        .documentType(doc.getDocumentType())
                        .fileName(doc.getFileName())
                        .uploadedAt(doc.getUploadedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownloadResponse downloadVaultDocument(UUID vaultId, UUID documentId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId).vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        LegalDocument document = legalDocumentRepository.findByIdAndVaultId(documentId, vault.getId())
                .orElseThrow(() -> new BeneficiaryException("Không tìm thấy tài liệu.", HttpStatus.NOT_FOUND));

        byte[] plain;
        try {
            plain = cryptoService.decryptBytes(fileStorageService.readFile(document.getFileUrlEncrypted()));
        } catch (Exception e) {
            log.error("Document read/decrypt failed for document {}", document.getId(), e);
            auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.FAILURE, currentUserId,
                    vault.getBeneficiary().getEmail(), vault.getId(),
                    "LegalDocument", document.getId().toString(), "Read or decrypt failed");
            throw new BeneficiaryException(
                    "Không thể giải mã tài liệu. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String fileName = toSafeDocumentFileName(document.getFileName());

        auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.SUCCESS, currentUserId,
                vault.getBeneficiary().getEmail(), vault.getId(),
                "LegalDocument", document.getId().toString(), null);

        return DocumentDownloadResponse.builder()
                .fileName(fileName)
                .contentType(contentTypeOf(fileName))
                .content(plain)
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public CloseVaultPreviewResponse previewClose(UUID vaultId, UUID currentUserId) {
        AccessContext ctx = loadAccessibleVault(vaultId, currentUserId);
        Vault vault = ctx.vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        int total = digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE).size();
        List<InheritedAssetSummaryResponse> unviewed = digitalAssetRepository
                .findUnaccessedAssets(vault.getId(), ctx.claim().getId()).stream()
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
        AccessContext ctx = loadAccessibleVault(vaultId, currentUserId);
        Vault vault = ctx.vault();
        BeneficiaryClaim claim = ctx.claim();
        assertNotLocked(vault.getId(), currentUserId);

        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            throw new BeneficiaryException(CONFIRM_REQUIRED_MSG, HttpStatus.BAD_REQUEST);
        }

        assertViewSession(vault.getId(), currentUserId);

        List<DigitalAsset> unviewed = digitalAssetRepository.findUnaccessedAssets(vault.getId(), claim.getId());
        if (!unviewed.isEmpty() && !Boolean.TRUE.equals(request.getAcknowledgeUnviewed())) {
            throw new BeneficiaryException("Còn " + unviewed.size()
                    + " tài sản chưa xem. Sau khi đóng hồ sơ bạn sẽ không xem được nữa. "
                    + "Nếu vẫn muốn đóng, hãy gửi acknowledgeUnviewed = true.", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        if (claimRepository.markClaimed(claim.getId(), now) == 0) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        claim.setStatus(ClaimStatus.CLAIMED);
        claim.setClaimedAt(now);
        vault.setStatus(VaultStatus.CLAIMED);
        vaultRepository.save(vault);

        String detail = "unviewedCount=" + unviewed.size()
                + ", unviewedAssetIds=" + unviewed.stream().map(a -> a.getId().toString()).toList();
        auditLogService.log(AuditAction.CLAIM_COMPLETED, AuditResult.SUCCESS, currentUserId,
                vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(), detail);

        return CloseVaultResponse.builder()
                .vaultId(vault.getId())
                .vaultStatus(vault.getStatus())
                .claimStatus(claim.getStatus())
                .claimedAt(claim.getClaimedAt())
                .build();
    }

    private record DecryptedAsset(Vault vault, DigitalAsset asset, String secret, String notes) {
    }

    private record AccessContext(Vault vault, BeneficiaryClaim claim) {
    }

    private DecryptedAsset loadDecryptedAsset(UUID vaultId, UUID assetId, UUID currentUserId) {
        AccessContext ctx = loadAccessibleVault(vaultId, currentUserId);
        Vault vault = ctx.vault();
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        DigitalAsset asset = digitalAssetRepository
                .findByIdAndVaultIdAndStatus(assetId, vault.getId(), AssetStatus.ACTIVE)
                .orElseThrow(() -> new BeneficiaryException("Không tìm thấy tài sản.", HttpStatus.NOT_FOUND));

        DecryptedAsset decrypted;
        try {
            decrypted = new DecryptedAsset(vault, asset,
                    cryptoService.decrypt(asset.getEncryptedSecret()),
                    cryptoService.decrypt(asset.getNotesEncrypted()));
        } catch (Exception e) {
            log.error("Decrypt failed for asset {}", asset.getId(), e);
            throw new BeneficiaryException(
                    "Không thể giải mã tài sản. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        markContentAccessed(ctx.claim().getId(), asset.getId());
        return decrypted;
    }

    private void markContentAccessed(UUID claimId, UUID assetId) {
        assetAccessRecordRepository.insertIfAbsent(UUID.randomUUID(), claimId, assetId, LocalDateTime.now());
    }

    private String buildDownloadContent(DecryptedAsset decrypted) {
        DigitalAsset asset = decrypted.asset();
        String notes = decrypted.notes() == null || decrypted.notes().isBlank()
                ? "(không có)" : decrypted.notes();

        return "LegacyVault - Thông tin tài sản thừa kế\n"
                + "========================================\n"
                + "Tên tài sản: " + asset.getAssetName() + "\n"
                + "Loại tài sản: " + asset.getAssetType() + "\n"
                + "Thông tin truy cập: " + decrypted.secret() + "\n"
                + "Ghi chú: " + notes + "\n"
                + "Xuất lúc: " + ZonedDateTime.now(VN_ZONE).format(EXPORT_TIME_FORMAT) + "\n"
                + "\n"
                + "Vui lòng lưu giữ thông tin này an toàn và xóa tệp sau khi đã lưu.\n";
    }

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
        return base.codePoints()
                .limit(80)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

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

    private AccessContext loadAccessibleVault(UUID vaultId, UUID currentUserId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));

        User beneficiary = vault.getBeneficiary();
        if (beneficiary == null || !beneficiary.getId().equals(currentUserId)) {
            throw new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND);
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

        BeneficiaryClaim claim = claimRepository.findByVaultId(vault.getId())
                .orElseThrow(() -> new BeneficiaryException(NO_SESSION_MSG, HttpStatus.BAD_REQUEST));

        if (claim.getStatus() == ClaimStatus.CLAIMED) {
            throw new BeneficiaryException(ALREADY_CLAIMED_MSG, HttpStatus.CONFLICT);
        }
        if (claim.getStatus() == ClaimStatus.EXPIRED || LocalDateTime.now().isAfter(claim.getClaimDeadlineAt())) {
            throw new BeneficiaryException(CLAIM_ENDED_MSG, HttpStatus.GONE);
        }
        return new AccessContext(vault, claim);
    }

    private void assertNotLocked(UUID vaultId, UUID currentUserId) {
        if (verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatus(
                vaultId, currentUserId, VerificationStatus.FAILED)) {
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

    private void assertViewSession(UUID vaultId, UUID currentUserId) {
        boolean valid = verificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                vaultId, currentUserId, VerificationStatus.SUCCESS,
                LocalDateTime.now().minusMinutes(VIEW_SESSION_MINUTES));
        if (!valid) {
            throw new BeneficiaryException(
                    "Cần xác thực danh tính trước khi xem tài sản.", HttpStatus.FORBIDDEN);
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
}