package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DmsCheckinRepository extends JpaRepository<DmsCheckin, UUID> {
    List<DmsCheckin> findAllByDmsConfigIdOrderByOccurredAtDesc(UUID dmsConfigId);

    // FR-03: lịch sử check-in của Owner, thứ tự do Pageable quyết định.
    Page<DmsCheckin> findByDmsConfigId(UUID dmsConfigId, Pageable pageable);

    boolean existsByTokenJti(String tokenJti);

    // Cooldown check-in trong ứng dụng: đã có lần OWNER_RESPONDED kênh này sau mốc after chưa
    boolean existsByDmsConfigIdAndCheckinTypeAndChannelAndOccurredAtAfter(UUID dmsConfigId, DmsCheckinType checkinType,
                                                                        NotificationChannel channel, LocalDateTime after);
}
