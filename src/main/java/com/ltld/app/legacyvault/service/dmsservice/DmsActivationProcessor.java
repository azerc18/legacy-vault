package com.ltld.app.legacyvault.service.dmsservice;

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
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorRoleManager;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

    // Tên class làm loại thực thể, dùng chung cho Notification.referenceType và audit targetType.
    public static final String ENTITY_DMS_CONFIG = "DmsConfig";
    public static final String ENTITY_EXECUTOR_ASSIGNMENT = "ExecutorAssignment";

    // Nội dung thông báo kích hoạt — tách thành constant để dễ thay đổi nội dung / i18n sau này.
    // Tham số: Owner (tên + email), loại Executor (Primary/Backup), mã ExecutorAssignment.
    private static final String MSG_EXECUTOR_ACTIVATED =
            "The Dead Man's Switch of %s has been triggered. You are the %s executor.%n"
                    + "Please sign in to LegacyVault and acknowledge this notice (code: %s).";

    // B6: báo Admin khi DMS kích hoạt mà Owner không có Executor khả dụng. Tham số: Owner, mã DmsConfig.
    private static final String MSG_ADMIN_NO_EXECUTOR =
            "The Dead Man's Switch of %s has been triggered, but no executor is available "
                    + "(not assigned or revoked). The case must be handled manually (DmsConfig ID: %s).";
    static final String ADMIN_ROLE_CODE = "ADMIN";

    // B2: gửi lại thông báo FAILED tối đa MAX_ATTEMPTS lần (tính cả lần gửi đầu), giãn cách theo retryDelay.
    static final int MAX_ATTEMPTS = 20;

    // Q27 (B3): Owner chưa nhận được lần nhắc nào thì hoãn kích hoạt tối đa 3 lần, mỗi lần 24 giờ.
    static final int MAX_TRIGGER_DEFERRALS = 3;
    static final Duration TRIGGER_DEFERRAL = Duration.ofHours(24);

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final ExecutorRoleManager executorRoleManager;
    private final AuditLogService auditLogService;
    private final ExecutorNotificationService executorNotificationService;
    private final UserRepository userRepository;

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
        if (config.getRemindersSent() == 0 && config.getTriggerDeferrals() < MAX_TRIGGER_DEFERRALS
                && hasReminderSchedule(config)) {
            defer(config, now);
            return;
        }
        config.setStatus(DmsStatus.TRIGGERED);
        config.setTriggeredAt(now);
        dmsConfigRepository.save(config);
        dmsCheckinRepository.save(DmsCheckin.builder()
                .dmsConfig(config)
                .checkinType(DmsCheckinType.TIMEOUT)
                .channel(NotificationChannel.EMAIL)
                .occurredAt(now)
                .build());

        Optional<ExecutorAssignment> primary = executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY)
                .filter(a -> a.getStatus() != ExecutorStatus.REVOKED);
        // Không có Primary hợp lệ (chưa chỉ định hoặc đã bị thu hồi): kích hoạt thẳng Backup để không bị kẹt.
        Optional<ExecutorAssignment> target = primary.isPresent() ? primary : executorAssignmentRepository
                .findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP)
                .filter(a -> a.getStatus() == ExecutorStatus.INACTIVE);
        target.ifPresent(a -> markActive(a, now));

        flushBeforeSideEffects();
        audit(AuditAction.DMS_TRIGGERED, ENTITY_DMS_CONFIG, config.getId(), ownerDetail(ownerId));
        if (target.isPresent()) {
            notifyActivated(config, target.get(), now);
            return;
        }

        log.warn("FR-07: dmsConfig {} triggered but owner {} has no usable executor", config.getId(), ownerId);
        audit(AuditAction.DMS_TRIGGERED_NO_EXECUTOR, ENTITY_DMS_CONFIG, config.getId(), ownerDetail(ownerId));
        notifyAdminsAfterCommit(config);
    }

    /** B6: gửi mail cho mọi Admin ACTIVE sau commit, để hồ sơ không kẹt im lặng; lỗi gửi chỉ ghi log. */
    private void notifyAdminsAfterCommit(DmsConfig config) {
        String content = String.format(MSG_ADMIN_NO_EXECUTOR, ownerLabel(config.getOwner()), config.getId());
        Runnable send = () -> {
            List<User> admins;
            try {
                admins = userRepository.findActiveByRoleCode(ADMIN_ROLE_CODE);
            } catch (RuntimeException e) {
                log.warn("B6: cannot load admins to notify for dmsConfig {}", config.getId(), e);
                return;
            }
            if (admins.isEmpty()) {
                log.warn("B6: dmsConfig {} has no executor and there is no active admin to notify", config.getId());
            }
            for (User admin : admins) {
                try {
                    executorNotificationService.send(admin.getEmail(), content);
                } catch (RuntimeException e) {
                    log.warn("B6: notifying admin {} for dmsConfig {} failed: {}", admin.getId(), config.getId(),
                            e.getMessage());
                }
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
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
                config, primary.getExecutorUser().getId()).orElse(null);
        // BR-03 (B2): đồng hồ bắt đầu từ lúc gửi thành công nếu có; nếu mail chưa bao giờ gửi được
        // thì từ lúc kích hoạt, để Primary không liên lạc được vẫn bị leo thang sang Backup.
        LocalDateTime clockStart = activation != null && activation.getSentAt() != null
                ? activation.getSentAt()
                : primary.getActivatedAt();
        if (clockStart == null
                || clockStart.plusDays(config.getBackupActivationWaitDays()).isAfter(now)) {
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
        markActive(backup, now);

        flushBeforeSideEffects();
        audit(AuditAction.EXECUTOR_REVOKE, ENTITY_EXECUTOR_ASSIGNMENT, primary.getId(),
                ownerDetail(ownerId) + ", executorType=PRIMARY, reason=BR03_TIMEOUT, activatedBackupId=" + backup.getId());
        notifyActivated(config, backup, now);
    }

    /**
     * Q27 (B3): lùi hạn kích hoạt 24 giờ và đặt nextReminderAt = now để FR-03 gửi ngay một lần nhắc bù
     * với hạn mới. Hết MAX_TRIGGER_DEFERRALS lần thì trigger vẫn kích hoạt như bình thường.
     */
    private void defer(DmsConfig config, LocalDateTime now) {
        config.setTriggerDeferrals(config.getTriggerDeferrals() + 1);
        config.setNextCheckinDueAt(now.plus(TRIGGER_DEFERRAL));
        config.setNextReminderAt(now);
        // Flush trước khi ghi audit, cùng lý do với flushBeforeSideEffects.
        dmsConfigRepository.saveAndFlush(config);
        audit(AuditAction.DMS_TRIGGER_DEFERRED, ENTITY_DMS_CONFIG, config.getId(),
                ownerDetail(config.getOwner().getId()) + ", deferral=" + config.getTriggerDeferrals()
                        + ", nextCheckinDueAt=" + config.getNextCheckinDueAt());
    }

    // Cùng điều kiện với DmsReminderProcessor.prepareReminder: thiếu lịch (dòng tạo trước FR-03) thì không bao giờ
    // gửi được lần nhắc nào, hoãn chỉ làm chậm bàn giao 3 ngày.
    private static boolean hasReminderSchedule(DmsConfig config) {
        return config.getLastCheckinAt() != null && config.getCheckinIntervalDays() != null
                && config.getGracePeriodDays() != null && config.getReminderCount() != null;
    }

    private void markActive(ExecutorAssignment assignment, LocalDateTime now) {
        assignment.setStatus(ExecutorStatus.ACTIVE);
        assignment.setActivatedAt(now);
        executorAssignmentRepository.save(assignment);
        executorRoleManager.grant(assignment.getExecutorUser());
    }

    /**
     * B1: flush mọi thay đổi trạng thái (DmsConfig, ExecutorAssignment) để lỗi @Version nổ ra ngay tại đây,
     * TRƯỚC khi gửi mail và ghi audit. Audit được ghi AFTER_COMPLETION + REQUIRES_NEW nên vẫn lưu kể cả khi
     * transaction rollback; nếu flush muộn (lúc commit) sẽ để lại audit cho thay đổi không xảy ra.
     */
    private void flushBeforeSideEffects() {
        executorAssignmentRepository.flush();
    }

    private void notifyActivated(DmsConfig config, ExecutorAssignment assignment, LocalDateTime now) {
        sendNotification(assignment.getExecutorUser(), config.getId(), activationMessage(config.getOwner(), assignment), now);
        audit(AuditAction.EXECUTOR_ACTIVATED, ENTITY_EXECUTOR_ASSIGNMENT, assignment.getId(),
                ownerDetail(config.getOwner().getId()) + ", executorType=" + assignment.getExecutorType());
    }

    private void retryFailedNotification(DmsConfig config, ExecutorAssignment assignment, LocalDateTime now) {
        User recipient = assignment.getExecutorUser();
        findActivationNotification(config, recipient.getId())
                .filter(n -> n.getStatus() == NotificationStatus.FAILED
                        && n.getAttemptCount() < MAX_ATTEMPTS
                        && (n.getLastAttemptAt() == null
                                || !n.getLastAttemptAt().plus(retryDelay(n.getAttemptCount())).isAfter(now)))
                .ifPresent(n -> {
                    n.setAttemptCount(n.getAttemptCount() + 1);
                    n.setLastAttemptAt(now);
                    try {
                        executorNotificationService.send(recipient.getEmail(), n.getContent());
                        n.setStatus(NotificationStatus.SENT);
                        n.setSentAt(now);
                    } catch (RuntimeException e) {
                        log.warn("FR-07: retry {} of notification {} failed: {}", n.getAttemptCount(), n.getId(),
                                e.getMessage());
                    }
                    notificationRepository.save(n);
                });
    }

    /** Khoảng chờ trước lần gửi lại tiếp theo: 1h, 2h, 4h, 8h, 16h, sau đó 24h. */
    static Duration retryDelay(int attemptCount) {
        long hours = Math.min(24, 1L << Math.max(0, attemptCount - 1));
        return Duration.ofHours(hours);
    }

    // Nội dung lưu vào Notification.content nên lần gửi lại dùng đúng nội dung này.
    static String activationMessage(User owner, ExecutorAssignment assignment) {
        String executorType = assignment.getExecutorType() == ExecutorType.PRIMARY ? "Primary" : "Backup";
        return String.format(MSG_EXECUTOR_ACTIVATED, ownerLabel(owner), executorType, assignment.getId());
    }

    private static String ownerLabel(User owner) {
        return owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail()
                : owner.getFullName() + " (" + owner.getEmail() + ")";
    }

    private Optional<Notification> findActivationNotification(DmsConfig config, UUID recipientId) {
        return notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        recipientId, NotificationType.EXECUTOR_ACTIVATED, ENTITY_DMS_CONFIG, config.getId());
    }

    /** Gửi mail; lỗi gửi không được làm rollback trạng thái DMS mà được ghi nhận FAILED để gửi lại ở lượt quét sau. */
    private void sendNotification(User recipient, UUID referenceId, String content, LocalDateTime now) {
        NotificationStatus status;
        LocalDateTime sentAt = null;
        try {
            executorNotificationService.send(recipient.getEmail(), content);
            status = NotificationStatus.SENT;
            sentAt = now;
        } catch (RuntimeException e) {
            log.warn("FR-07: sending activation notification to executor {} failed: {}", recipient.getId(), e.getMessage());
            status = NotificationStatus.FAILED;
        }
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(NotificationType.EXECUTOR_ACTIVATED)
                .channel(NotificationChannel.EMAIL)
                .referenceType(ENTITY_DMS_CONFIG)
                .referenceId(referenceId)
                .content(content)
                .status(status)
                .sentAt(sentAt)
                .attemptCount(1)
                .lastAttemptAt(now)
                .build());
    }

    // userId/email = null: sự kiện do scheduler tự động kích hoạt, không có user nào thực hiện hành động.
    // vaultId = null: DMS gắn với Owner, không với một Vault cụ thể (một Owner có thể có nhiều Vault).
    private void audit(AuditAction action, String targetType, UUID targetId, String detail) {
        auditLogService.log(action, AuditResult.SUCCESS, null, null, null,
                targetType, targetId.toString(), detail);
    }

    static String ownerDetail(UUID ownerId) {
        return "ownerId=" + ownerId;
    }
}
