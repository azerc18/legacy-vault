package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.enums.DmsStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // FR-06: SELECT ... FOR UPDATE và tăng @Version NGAY lúc đọc, để thao tác của Owner và lượt trigger
    // của FR-07 không thể cùng thành công, và xung đột nổ ra trước khi ghi audit.
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select c from DmsConfig c where c.owner.id = :ownerId")
    Optional<DmsConfig> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    List<DmsConfig> findAllByStatus(DmsStatus status);

    List<DmsConfig> findAllByStatusAndNextCheckinDueAtBefore(DmsStatus status, LocalDateTime deadline);
}
