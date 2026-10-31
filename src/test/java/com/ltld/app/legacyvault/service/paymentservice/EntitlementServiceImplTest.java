package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EntitlementServiceImplTest {

    private final EntitlementService entitlementService = new EntitlementServiceImpl();

    @Test
    void stubMethods_neverThrow() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();

        assertThatCode(() -> entitlementService.assertCanCreateVault(userId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanAddBeneficiary(vaultId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanAddBeneficiary(vaultId, 3)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanUseAdvancedAllocation(userId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanUseTrustedConfirmers(userId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanUseLegacyMessages(userId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertStorageQuota(userId, 1024L * 1024L)).doesNotThrowAnyException();
    }

    @Test
    void planLimitReachedException_hasForbiddenStatusAndErrorCode() {
        PlanLimitReachedException ex = new PlanLimitReachedException("Plan limit reached");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_REACHED);
        assertThat(ex.getMessage()).isEqualTo("Plan limit reached");
    }
}
