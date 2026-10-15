package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutorAssignmentRepository extends JpaRepository<ExecutorAssignment, UUID> {
    List<ExecutorAssignment> findAllByOwnerId(UUID ownerId);

    Optional<ExecutorAssignment> findByOwnerIdAndExecutorType(UUID ownerId, ExecutorType executorType);

    // FR-02 (Q26): email này có đang là Executor chưa bị thu hồi của Owner không (email truyền vào đã lowercase).
    @Query("select count(a) > 0 from ExecutorAssignment a where a.owner.id = :ownerId and a.status <> com.ltld.app.legacyvault.enums.ExecutorStatus.REVOKED and lower(a.executorUser.email) = :email")
    boolean existsLiveExecutorWithEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);
}
