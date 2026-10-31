package com.ltld.app.legacyvault.service.paymentservice;

import java.util.UUID;

/**
 * T1: Kiểm tra quyền lợi theo gói thuê bao (FREE / VIP).
 * Ném {@link com.ltld.app.legacyvault.exception.PlanLimitReachedException} (403, PLAN_LIMIT_REACHED) khi chạm giới hạn.
 */
public interface EntitlementService {

    /**
     * Gọi trước khi tạo vault mới.
     */
    void assertCanCreateVault(UUID ownerId);

    /**
     * Owner được phép có tổng cộng resultingCount beneficiary trong một vault không (FREE ≤ 2, VIP ≤ 10). Gọi TRƯỚC khi lưu.
     * N1 (tạo vault): resultingCount = số email đã bỏ trùng trong request.
     * N2-owner (thêm 1 người): resultingCount = số dòng vault_beneficiaries hiện có + 1.
     */
    void assertCanAddBeneficiaries(UUID ownerId, int resultingCount);

    /**
     * Kiểm tra quyền sử dụng phân bổ nâng cao (Chia tỷ lệ % PROPORTIONAL, Người nhận dự phòng contingentBeneficiary,
     * và từ đợt 2: Đồng thuận K-of-N JOINT_CONSENSUS / Shamir X1).
     */
    void assertCanUseAdvancedAllocation(UUID ownerId);

    /**
     * Kiểm tra quyền sử dụng tính năng Người xác nhận chéo DMS (Trusted Confirmers - X2).
     */
    void assertCanUseTrustedConfirmers(UUID ownerId);

    void assertCanUseLegacyMessages(UUID ownerId);

    /**
     * additionalBytes = kích thước file SẮP lưu (sau mã hóa); bản thật cộng với dung lượng đã dùng của Owner.
     */
    void assertStorageQuota(UUID ownerId, long additionalBytes);
}
