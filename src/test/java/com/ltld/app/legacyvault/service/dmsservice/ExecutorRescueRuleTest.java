package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorRescueRuleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    private static DmsConfig config(DmsStatus status) {
        return DmsConfig.builder().status(status).backupActivationWaitDays(14).build();
    }

    private static ExecutorAssignment assignment(ExecutorType type, ExecutorStatus status,
                                                 UserStatus userStatus, LocalDateTime activatedAt) {
        User u = User.builder().status(userStatus).build();
        return ExecutorAssignment.builder().executorType(type).executorUser(u)
                .status(status).activatedAt(activatedAt).build();
    }

    private static ExecutorAssignment activePrimary(LocalDateTime activatedAt) {
        return assignment(ExecutorType.PRIMARY, ExecutorStatus.ACTIVE, UserStatus.ACTIVE, activatedAt);
    }

    private static boolean rescue(DmsStatus dms, List<ExecutorAssignment> all, boolean submitted) {
        return ExecutorRescueRule.rescueNeeded(config(dms), all, NOW, a -> submitted);
    }

    @Test
    void notYetOverdue_noRescue() {
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(activePrimary(NOW.minusDays(5))), false)).isFalse();
    }

    @Test
    void overdueButSubmitted_noRescue() {
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(activePrimary(NOW.minusDays(20))), true)).isFalse();
    }

    @Test
    void overdueAndNotSubmitted_rescue() {
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(activePrimary(NOW.minusDays(20))), false)).isTrue();
    }

    @Test
    void dmsNotTriggered_noRescue() {
        assertThat(rescue(DmsStatus.ACTIVE, List.of(activePrimary(NOW.minusDays(20))), false)).isFalse();
    }

    // m3(a): đúng mốc activatedAt + waitDays thì đã tính là bỏ dở; sớm hơn 1 giây thì chưa
    @Test
    void exactlyAtDeadline_isStalled_oneSecondBefore_isNot() {
        DmsConfig cfg = config(DmsStatus.TRIGGERED);
        assertThat(ExecutorRescueRule.isStalled(activePrimary(NOW.minusDays(14)), cfg, NOW, a -> false)).isTrue();
        assertThat(ExecutorRescueRule.isStalled(activePrimary(NOW.minusDays(14).plusSeconds(1)), cfg, NOW, a -> false))
                .isFalse();
    }

    // Đoản mạch: chưa quá hạn thì không được truy vấn "đã nộp hồ sơ chưa"
    @Test
    void notOverdue_doesNotQueryHasSubmitted() {
        AtomicInteger calls = new AtomicInteger();
        ExecutorRescueRule.rescueNeeded(config(DmsStatus.TRIGGERED), List.of(activePrimary(NOW.minusDays(1))), NOW,
                a -> { calls.incrementAndGet(); return false; });
        assertThat(calls).hasValue(0);
    }

    // m3(b): User bị khóa thì không dùng được dù assignment còn ACTIVE và chưa quá hạn
    @Test
    void lockedUser_notUsable_rescue() {
        var a = assignment(ExecutorType.PRIMARY, ExecutorStatus.ACTIVE, UserStatus.LOCKED, NOW.minusDays(1));
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(a), false)).isTrue();
    }

    // m3(c): một Executor bỏ dở nhưng còn một Executor ACTIVE dùng được -> không cứu
    @Test
    void oneStalledOneUsable_noRescue() {
        var stalled = activePrimary(NOW.minusDays(20));
        var usable = assignment(ExecutorType.BACKUP, ExecutorStatus.ACTIVE, UserStatus.ACTIVE, NOW.minusDays(1));
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(stalled, usable), false)).isFalse();
    }

    // m3(d) / M1: Primary bỏ dở nhưng có Backup INACTIVE (user ACTIVE) đang chờ -> không cứu
    @Test
    void standbyBackup_noRescue() {
        var primary = activePrimary(NOW.minusDays(20));
        var backup = assignment(ExecutorType.BACKUP, ExecutorStatus.INACTIVE, UserStatus.ACTIVE, null);
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(primary, backup), false)).isFalse();
    }

    @Test
    void standbyBackupWithLockedUser_rescue() {
        var primary = activePrimary(NOW.minusDays(20));
        var backup = assignment(ExecutorType.BACKUP, ExecutorStatus.INACTIVE, UserStatus.LOCKED, null);
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(primary, backup), false)).isTrue();
    }

    // Sau leo thang BR-03: Primary REVOKED, Backup ACTIVE rồi bỏ dở, không còn Backup chờ -> cứu
    @Test
    void escalatedBackupStalled_rescue() {
        var primary = assignment(ExecutorType.PRIMARY, ExecutorStatus.REVOKED, UserStatus.ACTIVE, NOW.minusDays(40));
        var backup = assignment(ExecutorType.BACKUP, ExecutorStatus.ACTIVE, UserStatus.ACTIVE, NOW.minusDays(20));
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(primary, backup), false)).isTrue();
    }

    // M1: Primary đã xác nhận nhưng bỏ dở, vẫn còn Backup chờ -> không cứu (Admin phải kích hoạt Backup, không gán người ngoài)
    @Test
    void acknowledgedPrimaryStalled_withStandbyBackup_noRescue() {
        var primary = activePrimary(NOW.minusDays(20));
        primary.setAcknowledgedAt(NOW.minusDays(19));
        var backup = assignment(ExecutorType.BACKUP, ExecutorStatus.INACTIVE, UserStatus.ACTIVE, null);
        assertThat(rescue(DmsStatus.TRIGGERED, List.of(primary, backup), false)).isFalse();
    }
}