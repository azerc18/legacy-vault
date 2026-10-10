package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.IdentityVerification;
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
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.MockKycVerifier;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final int VERIFICATION_TTL_MINUTES = 15;
    private static final String NOT_FOUND_MSG = "Không tìm thấy yêu cầu nhận tài sản.";
    private static final int CLAIM_TIMEOUT_DAYS = 60;

    // FR-17: các tham số dễ chỉnh. SRS không quy định con số cụ thể.
    private static final int OTP_TTL_MINUTES = 5;
    private static final int OTP_RESEND_SECONDS = 60;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int VIEW_SESSION_MINUTES = 30;

    private static final String ALREADY_CLAIMED_MSG = "Tài sản này đã được nhận.";
    private static final String CLAIM_ENDED_MSG = "Thời hạn yêu cầu nhận tài sản đã kết thúc.";
    private static final String NOT_READY_MSG = "Tài sản chưa sẵn sàng để nhận.";
    private static final String NO_SESSION_MSG = "Chưa có phiên xác thực. Vui lòng khởi tạo yêu cầu nhận tài sản trước.";
    private static final String LOCKED_MSG = "Truy cập đã bị tạm khóa do xác thực sai nhiều lần. Vui lòng liên hệ Admin để được hỗ trợ.";

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final DigitalAssetRepository digitalAssetRepository;
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

    // ===================== FR-16 =====================

    @Override
    @Transactional
    public BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request, UUID currentUserId) {

        // 1. Kiểm tra Vault và Phân quyền (Ngăn lộ Vault ID)
        Vault vault = vaultRepository.findById(request.getVaultId())
                .orElseThrow(() -> new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND));

        User beneficiary = vault.getBeneficiary();
        if (beneficiary == null || !beneficiary.getId().equals(currentUserId)) {
            throw new BeneficiaryException(NOT_FOUND_MSG, HttpStatus.NOT_FOUND);
        }

        // 2. Kiểm tra trạng thái Vault
        if (vault.getStatus() == VaultStatus.CLAIMED) {
            throw new BeneficiaryException("Tài sản này đã được nhận.", HttpStatus.CONFLICT);
        }
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new BeneficiaryException("Thời hạn yêu cầu nhận tài sản đã kết thúc.", HttpStatus.GONE);
        }
        if (vault.getStatus() != VaultStatus.UNLOCKED) {
            throw new BeneficiaryException("Tài sản chưa sẵn sàng để nhận.", HttpStatus.BAD_REQUEST);
        }

        // 3. Khởi tạo hoặc lấy BeneficiaryClaim hiện tại
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

        // 4. Chống spam: Tìm phiên PENDING còn hiệu lực
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
        Vault vault = loadAccessibleVault(request.getVaultId(), currentUserId);
        assertNotLocked(vault.getId(), currentUserId);

        if (request.getVerificationMethod() != VerificationMethod.OTP) {
            throw new BeneficiaryException("Phương thức xác thực này không dùng OTP.", HttpStatus.BAD_REQUEST);
        }

        IdentityVerification verification =
                findPendingSession(vault.getId(), currentUserId, VerificationMethod.OTP);

        LocalDateTime now = LocalDateTime.now();

        // Không gửi OTP khi phiên không còn đủ thời gian cho một OTP trọn vẹn
        LocalDateTime sessionEnd = verification.getCreatedAt().plusMinutes(VERIFICATION_TTL_MINUTES);
        if (now.plusMinutes(OTP_TTL_MINUTES).isAfter(sessionEnd)) {
            throw new BeneficiaryException(
                    "Phiên xác thực sắp hết hạn. Vui lòng khởi tạo lại yêu cầu nhận tài sản.", HttpStatus.BAD_REQUEST);
        }

        // Chặn gửi lại quá nhanh, dựa vào thời điểm gửi lần trước được lưu tường minh
        if (verification.getOtpSentAt() != null
                && now.isBefore(verification.getOtpSentAt().plusSeconds(OTP_RESEND_SECONDS))) {
            throw new BeneficiaryException(
                    "Vui lòng đợi một lúc trước khi yêu cầu gửi lại OTP.", HttpStatus.TOO_MANY_REQUESTS);
        }

        // Gửi lại KHÔNG reset attemptCount, tránh lách giới hạn số lần thử
        String otp = otpGenerator.generate();
        verification.setOtpCode(otpHasher.hash(verification.getId(), otp)); // DB chỉ giữ hash
        verification.setOtpSentAt(now);
        verification.setOtpExpiresAt(now.plusMinutes(OTP_TTL_MINUTES));
        verificationRepository.save(verification);

        // OTP gốc chỉ tồn tại trong bộ nhớ và email, không bao giờ ghi DB hay log
        emailSender.sendEmail(vault.getBeneficiary().getEmail(), otp);
        auditLogService.log(AuditAction.IDENTITY_OTP_SENT, AuditResult.SUCCESS, currentUserId,
                vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(), null);
    }


    @Override
    @Transactional(noRollbackFor = BeneficiaryException.class)
    public VerifyIdentityResponse verifyIdentity(VerifyIdentityRequest request, UUID currentUserId) {
        Vault vault = loadAccessibleVault(request.getVaultId(), currentUserId);
        assertNotLocked(vault.getId(), currentUserId);

        IdentityVerification verification =
                findPendingSession(vault.getId(), currentUserId, request.getVerificationMethod());

        LocalDateTime now = LocalDateTime.now();
        boolean isOtp = request.getVerificationMethod() == VerificationMethod.OTP;

        // 1. Kiểm tra đầu vào. Các lỗi này không tính vào số lần thử.
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
                    vault.getBeneficiary().getEmail(), "Vault", vault.getId().toString(), "attempts=" + totalAttempts);
            throw new BeneficiaryException(LOCKED_MSG, HttpStatus.LOCKED);
        }

        // 4. Chấm đáp án
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

        // 5. Thành công
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
        Vault vault = loadAccessibleVault(vaultId, currentUserId);
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
    @Transactional(readOnly = true)
    public InheritedAssetDetailResponse getInheritedAssetDetail(UUID vaultId, UUID assetId, UUID currentUserId) {
        Vault vault = loadAccessibleVault(vaultId, currentUserId);
        assertNotLocked(vault.getId(), currentUserId);
        assertViewSession(vault.getId(), currentUserId);

        DigitalAsset asset = digitalAssetRepository
                .findByIdAndVaultIdAndStatus(assetId, vault.getId(), AssetStatus.ACTIVE)
                .orElseThrow(() -> new BeneficiaryException("Không tìm thấy tài sản.", HttpStatus.NOT_FOUND));

        InheritedAssetDetailResponse response;
        try {
            // Giải mã trong bộ nhớ cho phiên xem, không lưu lại bản rõ
            response = InheritedAssetDetailResponse.builder()
                    .id(asset.getId())
                    .assetType(asset.getAssetType())
                    .assetName(asset.getAssetName())
                    .secret(cryptoService.decrypt(asset.getEncryptedSecret()))
                    .notes(cryptoService.decrypt(asset.getNotesEncrypted()))
                    .build();
        } catch (Exception e) {
            // Không log dữ liệu đã giải mã, chỉ log id tài sản
            log.error("Decrypt failed for asset {}", asset.getId(), e);
            throw new BeneficiaryException(
                    "Không thể giải mã tài sản. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Chỉ ghi audit khi giải mã thành công, và không đưa dữ liệu rõ vào log
        auditLogService.log(AuditAction.ASSET_VIEWED, AuditResult.SUCCESS, currentUserId,
                vault.getBeneficiary().getEmail(), "DigitalAsset", asset.getId().toString(), null);
        return response;
    }

    // ===================== Helper dùng chung cho FR-17 =====================

    // Kiểm tra quyền và trạng thái dùng chung cho FR-17 (cùng quy tắc với FR-16)
    private Vault loadAccessibleVault(UUID vaultId, UUID currentUserId) {
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
        return vault;
    }

    // Khóa theo (vault, beneficiary): đã có phiên FAILED thì không tạo phiên mới để lách
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

    // Phiên xem hợp lệ = có phiên SUCCESS với verifiedAt trong VIEW_SESSION_MINUTES gần nhất
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