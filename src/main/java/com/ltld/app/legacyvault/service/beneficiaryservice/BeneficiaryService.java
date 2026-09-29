package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;

public interface BeneficiaryService {
    // Chỉ khai báo hàm cho FR-16 ở nhánh này
    BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request);
}