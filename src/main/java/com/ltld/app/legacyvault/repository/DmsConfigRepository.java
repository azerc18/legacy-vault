package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.enums.DmsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DmsConfigRepository extends JpaRepository<DmsConfig, UUID> {
    Optional<DmsConfig> findByOwnerId(UUID ownerId);

    List<DmsConfig> findAllByStatus(DmsStatus status);

    List<DmsConfig> findAllByStatusAndNextCheckinDueAtBefore(DmsStatus status, LocalDateTime deadline);
}
