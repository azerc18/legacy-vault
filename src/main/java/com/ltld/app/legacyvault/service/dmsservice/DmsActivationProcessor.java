package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-07: xử lý kích hoạt DMS / leo thang Backup cho MỘT Owner trong MỘT transaction riêng,
 * để lỗi của một Owner không làm rollback hay chặn các Owner khác trong cùng lượt quét.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmsActivationProcessor {

    public static final String DMS_REFERENCE_TYPE = "dms_config";
    private static final String CHANNEL_EMAIL = "EMAIL"; // hiện chỉ triển khai kênh email (SRS/contract: EMAIL)

    // Nội dung thông báo kích hoạt — tách thành constant để dễ thay đổi nội dung / i18n sau này.
    private static final String MSG_PRIMARY_ACTIVATED =
            "Quy trình bàn giao LegacyVault đã được kích hoạt. Vui lòng đăng nhập và xác nhận đã nhận thông báo.";
    private static final String MSG_BACKUP_ACTIVATED =
            "Quyền Digital Executor của bạn đã được kích hoạt. Vui lòng đăng nhập và xác nhận đã nhận thông báo.";

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final ExecutorNotificationService executorNotificationService;

    /** Loại thông báo kích hoạt tương ứng với từng loại Executor. */
    public static NotificationType activationNotificationType(ExecutorType type) {
        return type == ExecutorType.PRIMARY ? NotificationType.DMS_TRIGGERED : NotificationType.EXECUTOR_ACTIVATED;
    }

    /** Bước 1-3 của FR-07: DMS quá hạn -> TRIGGERED -> kích hoạt và báo Primary Executor. */
    @Transactional
    public void trigger(UUID configId, LocalDateTime now) {
        DmsConfig config = dmsConfigRepository.findById(configId).orElse(null);
        // Kiểm tra lại trong transaction: Owner có thể vừa check-in hoặc job khác đã xử lý.
        if (config == null || config.getStatus() != DmsStatus.ACTIVE
                || config.getNextCheckinDueAt() == null || !config.getNextCheckinDueAt().isBefore(now)) {
            return;
        }

        UUID ownerId = config.getOwner().getId();
        config.setStatus(DmsStatus.TRIGGERED);
        config.setTriggeredAt(now);
        dmsConfigRepository.save(config);
        dmsCheckinRepository.save(DmsCheckin.builder()
                .dmsConfig(config)
                .checkinType(DmsCheckinType.TIMEOUT)
                .channel(CHANNEL_EMAIL)
                .occurredAt(now)
                .build());
        // actor = null: sự kiện do scheduler tự động kích hoạt, không có user nào thực hiện hành động.
        saveAudit(null, AuditAction.DMS_TRIGGERED, "DmsConfig", config.getId(), ownerMap(ownerId));

        Optional<ExecutorAssignment> primary = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY)
                .filter(a -> a.getStatus() != ExecutorStatus.REVOKED);
        if (primary.isPresent()) {
            activate(config, primary.get(), AuditAction.PRIMARY_EXECUTOR_ACTIVATED, MSG_PRIMARY_ACTIVATED, now);
            return;
        }

        // Không có Primary hợp lệ (chưa chỉ định hoặc đã bị thu hồi): kích hoạt thẳng Backup để không bị kẹt.
        Optional<ExecutorAssignment> backup = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP)
                .filter(a -> a.getStatus() == ExecutorStatus.INACTIVE);
        if (backup.isPresent()) {
            activate(config, backup.get(), AuditAction.BACKUP_EXECUTOR_ACTIVATED, MSG_BACKUP_ACTIVATED, now);
            return;
        }

        log.warn("FR-07: dmsConfig {} triggered but owner {} has no usable executor", config.getId(), ownerId);
        // actor = null: sự kiện do scheduler tự động kích hoạt, không có user nào thực hiện hành động.
        saveAudit(null, AuditAction.DMS_TRIGGERED_NO_EXECUTOR, "DmsConfig", config.getId(), ownerMap(ownerId));
    }

    /**
     * Ngoại lệ 3a của FR-07: Primary không xác nhận sau N ngày (mặc định 14, BR-03)
     * -> thu hồi hoàn toàn Primary, kích hoạt Backup. Đồng thời gửi lại thông báo đã FAILED.
     */
    @Transactional
    public void escalateIfPrimaryUnresponsive(UUID configId, LocalDateTime now) {
        DmsConfig config = dmsConfigRepository.findById(configId).orElse(null);
        if (config == null || config.getStatus() != DmsStatus.TRIGGERED) {
            return;
        }
        UUID ownerId = config.getOwner().getId();

        for (ExecutorAssignment assignment : executorAssignmentRepository.findAllByOwnerId(ownerId)) {
            if (assignment.getStatus() == ExecutorStatus.ACTIVE) {
                retryFailedNotification(config, assignment, now);
            }
        }

        ExecutorAssignment primary = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY).orElse(null);
        if (primary == null || primary.getStatus() != ExecutorStatus.ACTIVE || primary.getAcknowledgedAt() != null) {
            return;
        }

        Notification activation = findActivationNotification(
                config, primary.getExecutorUser().getId(), NotificationType.DMS_TRIGGERED).orElse(null);
        // Đồng hồ 14 ngày chỉ bắt đầu khi thông báo đã gửi thành công.
        if (activation == null || activation.getStatus() == NotificationStatus.FAILED
                || activation.getSentAt() == null
                || activation.getSentAt().plusDays(config.getBackupActivationWaitDays()).isAfter(now)) {
            return;
        }

        Optional<ExecutorAssignment> backupOptional = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP)
                .filter(a -> a.getStatus() == ExecutorStatus.INACTIVE);
        if (backupOptional.isEmpty()) {
            log.warn("FR-07: primary {} did not respond but owner {} has no inactive backup", primary.getId(), ownerId);
            return;
        }

        ExecutorAssignment backup = backupOptional.get();
        primary.setStatus(ExecutorStatus.REVOKED);
        primary.setRevokedAt(now);
        executorAssignmentRepository.save(primary);
        Map<String, Object> metadata = Map.of(
                "ownerId", ownerId.toString(),
                "revokedPrimaryId", primary.getId().toString(),
                "activatedBackupId", backup.getId().toString()
        );
        // actor = null: sự kiện do scheduler tự động kích hoạt, không có user nào thực hiện hành động.
        saveAudit(null, AuditAction.PRIMARY_EXECUTOR_REVOKED, "ExecutorAssignment", primary.getId(), metadata);
        activate(config, backup, AuditAction.BACKUP_EXECUTOR_ACTIVATED, MSG_BACKUP_ACTIVATED, now);
    }

    private void activate(DmsConfig config, ExecutorAssignment assignment, AuditAction auditAction,
                          String content, LocalDateTime now) {
        assignment.setStatus(ExecutorStatus.ACTIVE);
        assignment.setActivatedAt(now);
        executorAssignmentRepository.save(assignment);
        sendNotification(assignment.getExecutorUser(), activationNotificationType(assignment.getExecutorType()),
                config.getId(), content, now);
        saveAudit(null, auditAction, "ExecutorAssignment", assignment.getId(), ownerMap(config.getOwner().getId()));
    }

    private void retryFailedNotification(DmsConfig config, ExecutorAssignment assignment, LocalDateTime now) {
        User recipient = assignment.getExecutorUser();
        findActivationNotification(config, recipient.getId(), activationNotificationType(assignment.getExecutorType()))
                .filter(n -> n.getStatus() == NotificationStatus.FAILED)
                .ifPresent(n -> {
                    try {
                        executorNotificationService.send(recipient.getEmail(), n.getContent());
                        n.setStatus(NotificationStatus.SENT);
                        n.setSentAt(now);
                        notificationRepository.save(n);
                    } catch (RuntimeException e) {
                        log.warn("FR-07: retry sending notification {} failed: {}", n.getId(), e.getMessage());
                    }
                });
    }

    private Optional<Notification> findActivationNotification(DmsConfig config, UUID recipientId, NotificationType type) {
        return notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        recipientId, type, DMS_REFERENCE_TYPE, config.getId());
    }

    /** Gửi mail; lỗi gửi không được làm rollback trạng thái DMS mà được ghi nhận FAILED để gửi lại ở lượt quét sau. */
    private void sendNotification(User recipient, NotificationType type, UUID referenceId, String content,
                                  LocalDateTime now) {
        NotificationStatus status;
        LocalDateTime sentAt = null;
        try {
            executorNotificationService.send(recipient.getEmail(), content);
            status = NotificationStatus.SENT;
            sentAt = now;
        } catch (RuntimeException e) {
            log.warn("FR-07: sending {} notification to executor {} failed: {}", type, recipient.getId(), e.getMessage());
            status = NotificationStatus.FAILED;
        }
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .channel(NotificationChannel.EMAIL)
                .referenceType(DMS_REFERENCE_TYPE)
                .referenceId(referenceId)
                .content(content)
                .status(status)
                .sentAt(sentAt)
                .build());
    }

    private void saveAudit(User actor, AuditAction action, String entityType, UUID entityId, Map<String, Object> metadata) {
        auditLogRepository.save(AuditLog.builder()
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata)
                .build());
    }

    private static Map<String, Object> ownerMap(UUID ownerId) {
        return Map.of("ownerId", ownerId.toString());
    }
}
