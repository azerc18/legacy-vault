package com.ltld.app.legacyvault.service.paymentservice;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub: cho phép tất cả. T1 sửa trực tiếp class này, không thêm bean EntitlementService thứ hai.
 */
@Service
public class EntitlementServiceImpl implements EntitlementService {

    @Override
    public void assertCanCreateVault(UUID ownerId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanAddBeneficiaries(UUID ownerId, int resultingCount) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanUseAdvancedAllocation(UUID ownerId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanUseTrustedConfirmers(UUID ownerId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertCanUseLegacyMessages(UUID ownerId) {
        // Stub: luôn cho phép
    }

    @Override
    public void assertStorageQuota(UUID ownerId, long additionalBytes) {
        // Stub: luôn cho phép
    }
}
