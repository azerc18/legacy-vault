package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;

import java.util.UUID;

public interface BeneficiaryService {
    /**
     * Khởi tạo phiên xác thực danh tính để Beneficiary bắt đầu nhận tài sản.
     * Trả 404 nếu vault không tồn tại hoặc người gọi không phải beneficiary,
     * 400 nếu vault chưa UNLOCKED, 409 nếu đã nhận, 410 nếu quá hạn claim.
     * Tái sử dụng phiên PENDING còn hiệu lực (15 phút) thay vì tạo mới.
     */
    BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request, UUID currentUserId);
}