package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.*;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {
    /**
     * Khởi tạo phiên xác thực danh tính để Beneficiary bắt đầu nhận tài sản.
     * Trả 404 nếu vault không tồn tại hoặc người gọi không phải beneficiary,
     * 400 nếu vault chưa UNLOCKED, 409 nếu đã nhận, 410 nếu quá hạn claim.
     * Tái sử dụng phiên PENDING còn hiệu lực (15 phút) thay vì tạo mới.
     */
    BeneficiaryClaimResponse initializeClaim(BeneficiaryClaimRequest request, UUID currentUserId);

    /**
     * FR-17: Gửi OTP qua email cho phiên xác thực PENDING của Beneficiary.
     * Lỗi: 404 (không phải beneficiary), 400/409/410 (vault hoặc claim không hợp lệ,
     * hoặc chưa có phiên PENDING), 423 (đã bị khóa), 429 (gửi lại quá sớm).
     */
    void sendIdentityOtp(BeneficiaryClaimRequest request, UUID currentUserId);

    /**
     * FR-17: Xác thực danh tính bằng OTP hoặc mock eKYC.
     * Sai quá số lần cho phép thì phiên chuyển FAILED và Beneficiary bị tạm khóa (423),
     * cần Admin hỗ trợ. Thành công thì mở phiên xem tài sản trong một khoảng thời gian.
     */
    VerifyIdentityResponse verifyIdentity(VerifyIdentityRequest request, UUID currentUserId);

    /**
     * FR-17: Danh sách tài sản còn hiệu lực trong Vault (chỉ thông tin tóm tắt).
     * Yêu cầu phiên xác thực SUCCESS còn trong thời gian xem, nếu không trả 403.
     */
    List<InheritedAssetSummaryResponse> getInheritedAssets(UUID vaultId, UUID currentUserId);

    /**
     * FR-17: Chi tiết một tài sản, secret và ghi chú được giải mã tạm thời cho phiên xem.
     * Yêu cầu phiên xác thực SUCCESS còn trong thời gian xem, nếu không trả 403.
     */
    InheritedAssetDetailResponse getInheritedAssetDetail(UUID vaultId, UUID assetId, UUID currentUserId);

    /**
     * FR-18: Tải xuống thông tin truy cập của một tài sản (đã giải mã) dưới dạng tệp văn bản.
     * Kiểm tra lại phiên xác thực: hết hạn thì trả 403 và yêu cầu xác thực lại.
     * Lỗi: 404 (không phải beneficiary hoặc không có tài sản), 423 (đã bị khóa),
     * 400/409/410 (vault hoặc claim không hợp lệ), 500 (giải mã thất bại).
     * Ghi audit ASSET_DOWNLOADED sau khi giải mã thành công.
     */
    AssetDownloadResponse downloadInheritedAsset(UUID vaultId, UUID assetId, UUID currentUserId);
}