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

    // Thông báo trong hệ thống của một người dùng, mới nhất trước
    List<Notification> findAllByRecipientIdAndChannelOrderByCreatedAtDesc(UUID recipientId, NotificationChannel channel);

    Optional<Notification> findByIdAndRecipientIdAndChannel(UUID id, UUID recipientId, NotificationChannel channel);
}
