package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntitlementServiceImplTest {

    private final EntitlementService entitlementService = new EntitlementServiceImpl();

    @Test
    void stubMethods_neverThrow() {
        UUID ownerId = UUID.randomUUID();

        assertThatCode(() -> entitlementService.assertCanCreateVault(ownerId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanAddBeneficiaries(ownerId, 2)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanUseAdvancedAllocation(ownerId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanUseTrustedConfirmers(ownerId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertCanUseLegacyMessages(ownerId)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementService.assertStorageQuota(ownerId, 1024L * 1024L)).doesNotThrowAnyException();
    }

    @Test
    void planLimitReachedException_hasForbiddenStatusAndErrorCodeAndFeature() {
        PlanLimitReachedException ex = new PlanLimitReachedException(PlanFeature.BENEFICIARY_LIMIT, "Plan limit reached");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_REACHED);
        assertThat(ex.getFeature()).isEqualTo(PlanFeature.BENEFICIARY_LIMIT);
        assertThat(ex.getMessage()).isEqualTo("Plan limit reached");
    }

    @Test
    void planLimitReachedException_nullFeature_throwsNullPointerException() {
        assertThatThrownBy(() -> new PlanLimitReachedException(null, "Plan limit reached"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("feature");
    }
}
