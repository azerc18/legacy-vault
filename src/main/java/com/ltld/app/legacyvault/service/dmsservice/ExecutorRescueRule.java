package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.function.Predicate;

public final class ExecutorRescueRule {
    private ExecutorRescueRule() {
    }

    public static boolean isUsable(ExecutorAssignment a) {
        return a.getStatus() == ExecutorStatus.ACTIVE
                && a.getExecutorUser() != null
                && a.getExecutorUser().getStatus() == UserStatus.ACTIVE;
    }

    // FR-07: đã kích hoạt quá backupActivationWaitDays mà chưa nộp hồ sơ nào sau lúc kích hoạt
    public static boolean isStalled(ExecutorAssignment a, DmsConfig config, LocalDateTime now,
                                    Predicate<ExecutorAssignment> hasSubmitted) {
        LocalDateTime start = a.getActivatedAt();
        return start != null
                && !start.plusDays(config.getBackupActivationWaitDays()).isAfter(now)
                && !hasSubmitted.test(a);
    }

    // FR-07: còn Backup chờ (INACTIVE, user ACTIVE) thì chưa cần Admin cứu, BR-03 / Owner đã có người dự phòng
    public static boolean hasStandbyBackup(Collection<ExecutorAssignment> all) {
        return all.stream().anyMatch(a -> a.getExecutorType() == ExecutorType.BACKUP
                && a.getStatus() == ExecutorStatus.INACTIVE
                && a.getExecutorUser() != null
                && a.getExecutorUser().getStatus() == UserStatus.ACTIVE);
    }

    public static boolean rescueNeeded(DmsConfig config, Collection<ExecutorAssignment> all,
                                       LocalDateTime now, Predicate<ExecutorAssignment> hasSubmitted) {
        return config != null
                && config.getStatus() == DmsStatus.TRIGGERED
                && all.stream().noneMatch(a -> isUsable(a) && !isStalled(a, config, now, hasSubmitted))
                && !hasStandbyBackup(all);
    }
}
