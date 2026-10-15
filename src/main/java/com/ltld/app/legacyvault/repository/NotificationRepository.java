package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationType;
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
}
