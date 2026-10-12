package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutorAssignmentRepository extends JpaRepository<ExecutorAssignment, UUID> {
    List<ExecutorAssignment> findAllByOwnerId(UUID ownerId);

    Optional<ExecutorAssignment> findByOwnerIdAndExecutorType(UUID ownerId, ExecutorType executorType);

    Optional<ExecutorAssignment> findByOwnerIdAndExecutorTypeAndStatus(
            UUID ownerId, ExecutorType executorType, ExecutorStatus status);

    // FR-06: user còn giữ assignment nào khác (của bất kỳ Owner nào) chưa bị thu hồi không.
    boolean existsByExecutorUserIdAndStatusNotAndIdNot(UUID executorUserId, ExecutorStatus status, UUID excludedId);
}
