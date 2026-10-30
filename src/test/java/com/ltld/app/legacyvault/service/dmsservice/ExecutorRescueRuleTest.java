package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorRescueRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    private static DmsConfig config(DmsStatus status) {
        return DmsConfig.builder().status(status).backupActivationWaitDays(14).build();
    }

    private static ExecutorAssignment active(LocalDateTime activatedAt) {
        User u = User.builder().status(UserStatus.ACTIVE).build();
        return ExecutorAssignment.builder()
                .executorUser(u).status(ExecutorStatus.ACTIVE).activatedAt(activatedAt).build();
    }

    @Test
    void notYetOverdue_noRescue() {
        var a = active(NOW.minusDays(5));
        assertThat(ExecutorRescueRule.rescueNeeded(config(DmsStatus.TRIGGERED), List.of(a), NOW, x -> false)).isFalse();
    }

    @Test
    void overdueButSubmitted_noRescue() {
        var a = active(NOW.minusDays(20));
        assertThat(ExecutorRescueRule.rescueNeeded(config(DmsStatus.TRIGGERED), List.of(a), NOW, x -> true)).isFalse();
    }

    @Test
    void overdueAndNotSubmitted_rescue() {
        var a = active(NOW.minusDays(20));
        assertThat(ExecutorRescueRule.rescueNeeded(config(DmsStatus.TRIGGERED), List.of(a), NOW, x -> false)).isTrue();
    }

    @Test
    void dmsNotTriggered_noRescue() {
        var a = active(NOW.minusDays(20));
        assertThat(ExecutorRescueRule.rescueNeeded(config(DmsStatus.ACTIVE), List.of(a), NOW, x -> false)).isFalse();
    }
}