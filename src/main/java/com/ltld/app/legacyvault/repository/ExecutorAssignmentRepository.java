package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutorAssignmentRepository extends JpaRepository<ExecutorAssignment, UUID> {
    List<ExecutorAssignment> findAllByOwnerId(UUID ownerId);

    // FR-03/FR-06: khóa FOR UPDATE mọi dòng Executor của Owner, để bật DMS và thu hồi Executor cuối cùng không write-skew.
    // Không join fetch: FOR UPDATE chỉ khóa executor_assignments (qua uk owner_id + executor_type).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExecutorAssignment a where a.owner.id = :ownerId")
    List<ExecutorAssignment> findAllByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    Optional<ExecutorAssignment> findByOwnerIdAndExecutorType(UUID ownerId, ExecutorType executorType);

    Optional<ExecutorAssignment> findByOwnerIdAndExecutorTypeAndStatus(
            UUID ownerId, ExecutorType executorType, ExecutorStatus status);

    // FR-06: user còn giữ assignment nào khác (của bất kỳ Owner nào) chưa bị thu hồi không.
    boolean existsByExecutorUserIdAndStatusNotAndIdNot(UUID executorUserId, ExecutorStatus status, UUID excludedId);

    // FR-02 (Q26): email này có đang là Executor chưa bị thu hồi của Owner không (email truyền vào đã lowercase).
    @Query("select count(a) > 0 from ExecutorAssignment a where a.owner.id = :ownerId and a.status <> com.ltld.app.legacyvault.enums.ExecutorStatus.REVOKED and lower(a.executorUser.email) = :email")
    boolean existsLiveExecutorWithEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);

    // B1: các assignment chưa bị thu hồi của một Executor (gồm nhiều Owner), mới nhất trước.
    @Query("select a from ExecutorAssignment a join fetch a.owner where a.executorUser.id = :userId and a.status <> com.ltld.app.legacyvault.enums.ExecutorStatus.REVOKED order by a.createdAt desc")
    List<ExecutorAssignment> findLiveByExecutorUserId(@Param("userId") UUID userId);

    // B1: như trên nhưng tra theo email (email truyền vào đã lowercase), khi principal là email thay vì user id.
    @Query("select a from ExecutorAssignment a join fetch a.owner where lower(a.executorUser.email) = :email and a.status <> com.ltld.app.legacyvault.enums.ExecutorStatus.REVOKED order by a.createdAt desc")
    List<ExecutorAssignment> findLiveByExecutorEmail(@Param("email") String email);
    // D3: Executor này còn assignment ACTIVE với Owner này không.
    boolean existsByOwnerIdAndExecutorUserIdAndStatus(UUID ownerId, UUID executorUserId, ExecutorStatus status);
}
