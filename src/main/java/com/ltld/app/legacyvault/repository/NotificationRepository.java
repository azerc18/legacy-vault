package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    Optional<Notification> findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
            UUID recipientId, NotificationType type, String referenceType, UUID referenceId);

    // FR-16: 50 thông báo trong hệ thống mới nhất của người dùng
    List<Notification> findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(UUID recipientId, NotificationChannel channel);

    Optional<Notification> findByIdAndRecipientIdAndChannel(UUID id, UUID recipientId, NotificationChannel channel);

    // FR-11 / FR-12 / FR-14 / FR-16: thông báo trong app của người dùng, phân trang
    Page<Notification> findByRecipientIdAndChannel(UUID recipientId, NotificationChannel channel, Pageable pageable);

    // FR-11 / FR-12 / FR-14 / FR-16: số thông báo trong app chưa đọc
    long countByRecipientIdAndChannelAndStatusNot(UUID recipientId, NotificationChannel channel,
                                                  NotificationStatus status);
}
