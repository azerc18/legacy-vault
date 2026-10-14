package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.enums.ExecutorType;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
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

    Optional<ExecutorAssignment> findByOwnerIdAndExecutorType(UUID ownerId, ExecutorType executorType);
}
