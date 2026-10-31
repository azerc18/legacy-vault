package com.ltld.app.legacyvault.service.paymentservice;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * T1 (Stub): Bản cài đặt tạm thời luôn cho phép mọi thao tác để không chặn N1 và F4a.
 * Sẽ được thay bằng logic kiểm tra gói FREE / VIP thực tế trong PR T1.
 */
@Service
public class EntitlementServiceImpl implements EntitlementService {

    @Override
    public void assertCanCreateVault(UUID userId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanAddBeneficiary(UUID vaultId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanUseAdvancedAllocation(UUID userId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanUseTrustedConfirmers(UUID userId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanUseLegacyMessages(UUID userId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertStorageQuota(UUID userId, long bytes) {
        // Stub: luôn cho phép
    }
}
