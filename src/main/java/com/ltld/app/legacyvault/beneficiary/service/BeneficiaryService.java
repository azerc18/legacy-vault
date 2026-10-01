package com.ltld.app.legacyvault.beneficiary.service;

import com.ltld.app.legacyvault.beneficiary.dto.AssetViewResponse;
import com.ltld.app.legacyvault.beneficiary.dto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.beneficiary.dto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.beneficiary.dto.VerifyIdentityRequest;

import java.util.UUID;

public interface BeneficiaryService {

    BeneficiaryClaimResponse processClaim(BeneficiaryClaimRequest request);

    // FR-17: Xác thực OTP/KYC và trả về tài sản giải mã
    AssetViewResponse verifyIdentityAndViewAsset(VerifyIdentityRequest request);

    // FR-19: Xác nhận đóng hồ sơ (Bonus)
    void confirmClaimClosing(UUID vaultId);
}
