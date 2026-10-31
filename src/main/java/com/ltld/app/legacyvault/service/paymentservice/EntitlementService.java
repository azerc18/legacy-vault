package com.ltld.app.legacyvault.service.paymentservice;

import java.util.UUID;

/**
 * T1: Kiểm tra hạn mức theo gói thuê bao (FREE / VIP).
 * Stub ban đầu luôn cho phép để các luồng N1 (tạo vault kèm beneficiary) và F4a (legacy messages) gọi trước mà không bị chặn.
 */
public interface EntitlementService {

    void assertCanCreateVault(UUID userId);

    void assertCanAddBeneficiary(UUID vaultId);

    default void assertCanAddBeneficiary(UUID vaultId, int additionalCount) {
        assertCanAddBeneficiary(vaultId);
    }

    void assertCanUseLegacyMessages(UUID userId);

    void assertStorageQuota(UUID userId, long bytes);
}
