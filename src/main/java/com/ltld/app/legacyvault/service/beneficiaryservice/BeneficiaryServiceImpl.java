package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final int VERIFICATION_TTL_MINUTES = 15;
    private static final String NOT_FOUND_MSG = "Không tìm thấy yêu cầu nhận tài sản.";
    private static final int CLAIM_TIMEOUT_DAYS = 60;
    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final IdentityVerificationRepository verificationRepository;


    private LocalDateTime resolveDeadline(Vault vault) {
        if (vault.getClaimDeadlineAt() != null) {
            return vault.getClaimDeadlineAt();
        }
        LocalDateTime base = vault.getUnlockedAt() != null ? vault.getUnlockedAt() : LocalDateTime.now();
        return base.plusDays(CLAIM_TIMEOUT_DAYS);
    }

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


}