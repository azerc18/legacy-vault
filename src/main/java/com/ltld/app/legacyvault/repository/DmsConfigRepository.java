package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.enums.DmsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // FR-03: id các DMS đã tới lần nhắc check-in kế tiếp (dùng index status, next_reminder_at).
    @Query("select c.id from DmsConfig c where c.status = :status and c.nextReminderAt <= :now")
    List<UUID> findIdsDueForReminder(@Param("status") DmsStatus status, @Param("now") LocalDateTime now);
}
