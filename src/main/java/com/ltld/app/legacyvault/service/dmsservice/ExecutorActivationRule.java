package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * B-01a: Executor nào sẽ được kích hoạt nếu DMS trigger lúc này.
 *
 * <p>Sao y cách {@code DmsActivationProcessor.trigger} chọn người: Primary chưa REVOKED, nếu không có thì
 * Backup đang INACTIVE. Trigger chưa dùng helper này (file đang được sửa ở nhánh khác); khi đổi một bên
 * phải đổi cả bên kia để bật DMS / hasUsableExecutor không lệch với trigger.
 */
public final class ExecutorActivationRule {

    private ExecutorActivationRule() {
    }

    public static boolean isUsablePrimary(ExecutorAssignment assignment) {
        return assignment.getExecutorType() == ExecutorType.PRIMARY && assignment.getStatus() != ExecutorStatus.REVOKED;
    }

    public static boolean isUsableBackup(ExecutorAssignment assignment) {
        return assignment.getExecutorType() == ExecutorType.BACKUP && assignment.getStatus() == ExecutorStatus.INACTIVE;
    }

    /** Người trigger sẽ kích hoạt trong các assignment của MỘT Owner; rỗng = DMS sẽ kích hoạt mà không có Executor. */
    public static Optional<ExecutorAssignment> pickActivationTarget(Collection<ExecutorAssignment> assignments) {
        return first(assignments, ExecutorActivationRule::isUsablePrimary)
                .or(() -> first(assignments, ExecutorActivationRule::isUsableBackup));
    }

    private static Optional<ExecutorAssignment> first(Collection<ExecutorAssignment> assignments,
                                                      Predicate<ExecutorAssignment> predicate) {
        return assignments.stream().filter(predicate).findFirst();
    }
}
