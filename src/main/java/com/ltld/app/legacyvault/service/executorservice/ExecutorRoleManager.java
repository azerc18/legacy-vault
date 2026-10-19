package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Thêm / gỡ role EXECUTOR, dùng chung cho FR-06 (Owner chỉ định, thay thế, thu hồi) và FR-07 (kích hoạt Executor).
 *
 * <p>Không có @Transactional: chạy trong transaction của nơi gọi. User là entity managed nên thay đổi roles
 * được lưu khi flush. FR-06 gọi sau khi đã khóa User (lockUsersInOrder); FR-07 gọi trong markActive.
 */
@Component
@RequiredArgsConstructor
public class ExecutorRoleManager {

    // Mã role Executor, trùng với DataInitializer.
    static final String ROLE_EXECUTOR = "EXECUTOR";

    private final RoleRepository roleRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;

    /** Idempotent: user đã có role EXECUTOR thì không làm gì. */
    public void grant(User user) {
        boolean hasRole = user.getRoles().stream().anyMatch(r -> ROLE_EXECUTOR.equals(r.getCode()));
        if (!hasRole) {
            Role role = roleRepository.findByCode(ROLE_EXECUTOR)
                    .orElseThrow(() -> new IllegalStateException("Role not found: " + ROLE_EXECUTOR));
            user.getRoles().add(role);
        }
    }

    /**
     * Gỡ role EXECUTOR nếu user không còn assignment nào chưa REVOKED (của bất kỳ Owner nào).
     * excludedAssignmentId là dòng vừa bị thu hồi/thay người, loại ra vì trạng thái mới của nó chưa được flush.
     */
    public void revokeIfUnused(User user, UUID excludedAssignmentId) {
        Objects.requireNonNull(excludedAssignmentId, "excludedAssignmentId");
        boolean stillAssigned = executorAssignmentRepository
                .existsByExecutorUserIdAndStatusNotAndIdNot(user.getId(), ExecutorStatus.REVOKED, excludedAssignmentId);
        if (!stillAssigned) {
            user.getRoles().removeIf(r -> ROLE_EXECUTOR.equals(r.getCode()));
        }
    }
}
