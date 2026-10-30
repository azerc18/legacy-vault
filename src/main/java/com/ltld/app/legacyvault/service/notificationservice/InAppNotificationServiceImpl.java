package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.dto.notificationdto.NotificationResponse;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InAppNotificationServiceImpl implements InAppNotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(UUID userId, int page, int size) {
        return notificationRepository.findByRecipientIdAndChannel(userId, NotificationChannel.IN_APP,
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(InAppNotificationServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByRecipientIdAndChannelAndStatusNot(
                userId, NotificationChannel.IN_APP, NotificationStatus.READ);
    }

    @Override
    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        // Thông báo của người khác trả 404 để không lộ sự tồn tại
        Notification notification = notificationRepository
                .findByIdAndRecipientIdAndChannel(notificationId, userId, NotificationChannel.IN_APP)
                .orElseThrow(() -> new BusinessException("Notification not found.", HttpStatus.NOT_FOUND));
        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ);
            notificationRepository.save(notification);
        }
    }

    private static NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .content(n.getContent())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .read(n.getStatus() == NotificationStatus.READ)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
