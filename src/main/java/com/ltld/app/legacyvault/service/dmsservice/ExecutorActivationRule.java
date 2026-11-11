package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * FR-03: Executor nào sẽ được kích hoạt nếu DMS trigger lúc này.
 *
 * <p>{@code DmsActivationProcessor} (trigger + leo thang Backup) dùng chung helper này: Primary chưa REVOKED,
 * nếu không có thì Backup đang INACTIVE, và cả hai đều phải đã ĐỒNG Ý lời mời (ACCEPTED). Khi đổi một bên
 * phải đổi cả bên kia để bật DMS / hasUsableExecutor không lệch với trigger.
 */
public final class ExecutorActivationRule {

    private ExecutorActivationRule() {
    }

    /** Executor đã đồng ý lời mời của Owner; người đang chờ phản hồi chưa được tính là Executor dùng được. */
    public static boolean isAccepted(ExecutorAssignment assignment) {
        return assignment.getResponseStatus() == ExecutorResponseStatus.ACCEPTED;
    }

    public static boolean isUsablePrimary(ExecutorAssignment assignment) {
        return assignment.getExecutorType() == ExecutorType.PRIMARY && assignment.getStatus() != ExecutorStatus.REVOKED
                && isAccepted(assignment);
    }

    public static boolean isUsableBackup(ExecutorAssignment assignment) {
        return assignment.getExecutorType() == ExecutorType.BACKUP && assignment.getStatus() == ExecutorStatus.INACTIVE
                && isAccepted(assignment);
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
