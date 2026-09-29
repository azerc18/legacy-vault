package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryClaimRepository claimRepository;
    private final IdentityVerificationRepository verificationRepository;

    @Override
    @Transactional
    public BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request) {

        // 1. TÌM KIẾM VÀ KIỂM TRA SỰ TỒN TẠI CỦA YÊU CẦU
        Optional<BeneficiaryClaim> optionalClaim = claimRepository.findByVaultId(request.getVaultId());

        if (optionalClaim.isEmpty()) {
            throw new RuntimeException("Không tìm thấy yêu cầu nhận tài sản cho Vault ID này.");
        }

        BeneficiaryClaim claim = optionalClaim.get();

        // 2. KIỂM TRA ĐIỀU KIỆN NGHIỆP VỤ (BUSINESS RULES VALIDATION)
        if (claim.getStatus() == ClaimStatus.CLAIMED) {
            throw new RuntimeException("Tài sản này đã được nhận, không thể yêu cầu lại.");
        }

        if (claim.getStatus() == ClaimStatus.EXPIRED || LocalDateTime.now().isAfter(claim.getClaimDeadlineAt())) {
            throw new RuntimeException("Thời hạn yêu cầu nhận tài sản đã kết thúc.");
        }

        // 3. KHỞI TẠO PHIÊN XÁC THỰC DANH TÍNH (LƯU VÀO BẢNG IDENTITY_VERIFICATIONS)
        IdentityVerification verification = IdentityVerification.builder()
                .beneficiary(claim.getBeneficiary()) // Lưu ý: Chỉnh sửa dựa trên Entity mapping thực tế của bạn
                .vault(claim.getVault())             // Lưu ý: Chỉnh sửa dựa trên Entity mapping thực tế của bạn
                .method(request.getVerificationMethod())
                .status(VerificationStatus.PENDING)
                .build();

        verificationRepository.save(verification);

        // 4. ĐÓNG GÓI DỮ LIỆU VÀ TRẢ VỀ CHO FRONTEND (MAPPING ENTITY -> DTO)
        return BeneficiaryClaimResponse.builder()
                .claimId(claim.getId())
                .vaultId(claim.getVault().getId())
                .status(claim.getStatus())
                .claimDeadlineAt(claim.getClaimDeadlineAt())
                .claimedAt(claim.getClaimedAt())
                .build();
    }
}