package com.ltld.app.legacyvault.service.paymentservice;

import java.util.UUID;

/**
 * T1: Kiểm tra quyền lợi theo gói thuê bao (FREE / VIP).
 * Ném {@link com.ltld.app.legacyvault.exception.PlanLimitReachedException} (403, PLAN_LIMIT_REACHED) khi chạm giới hạn.
 */
public interface EntitlementService {

    void assertCanCreateVault(UUID userId);

    void assertCanAddBeneficiary(UUID vaultId);

    default void assertCanAddBeneficiary(UUID vaultId, int currentBeneficiaryCount) {
        assertCanAddBeneficiary(vaultId);
    }

    /**
     * Kiểm tra quyền sử dụng phân bổ nâng cao (Chia tỷ lệ % PROPORTIONAL, Người nhận dự phòng contingentBeneficiary,
     * và từ đợt 2: Đồng thuận K-of-N JOINT_CONSENSUS / Shamir X1).
     */
    void assertCanUseAdvancedAllocation(UUID userId);

    /**
     * Kiểm tra quyền sử dụng tính năng Người xác nhận chéo DMS (Trusted Confirmers - X2).
     */
    void assertCanUseTrustedConfirmers(UUID userId);

    void assertCanUseLegacyMessages(UUID userId);

    void assertStorageQuota(UUID userId, long bytes);
}
