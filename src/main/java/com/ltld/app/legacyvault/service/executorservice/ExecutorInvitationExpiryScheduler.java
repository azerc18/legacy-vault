package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hạn trả lời lời mời làm Executor ({@link ExecutorInvitationPolicy}):
 * <ul>
 *   <li>còn ít ngày: gửi mail nhắc người được mời, đúng một lần (invitation_reminder_sent_at);</li>
 *   <li>quá hạn: thu hồi như khi bị từ chối — REVOKED + responseStatus EXPIRED, gỡ role EXECUTOR nếu người đó
 *       không còn làm Executor cho Owner nào — rồi báo Owner qua mail và chuông để chỉ định người khác.</li>
 * </ul>
 *
 * <p>Mỗi dòng xử lý trong transaction REQUIRES_NEW riêng và khóa lại trước khi kiểm tra điều kiện, vì người
 * được mời có thể vừa trả lời hoặc Owner vừa thu hồi. Một dòng lỗi không chặn các dòng còn lại.
 * Mặc định BẬT; tắt bằng app.executor.invitation-expiry.enabled=false.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.executor.invitation-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ExecutorInvitationExpiryScheduler {

    static final int BATCH_SIZE = 50;

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final UserRepository userRepository;
    private final ExecutorRoleManager executorRoleManager;
    private final ExecutorInvitationPolicy policy;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate requiresNewTx;

    public ExecutorInvitationExpiryScheduler(ExecutorAssignmentRepository executorAssignmentRepository,
                                             UserRepository userRepository,
                                             ExecutorRoleManager executorRoleManager,
                                             ExecutorInvitationPolicy policy,
                                             AuditLogService auditLogService,
                                             ApplicationEventPublisher eventPublisher,
                                             PlatformTransactionManager transactionManager) {
        this.executorAssignmentRepository = executorAssignmentRepository;
        this.userRepository = userRepository;
        this.executorRoleManager = executorRoleManager;
        this.policy = policy;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Chạy mỗi giờ; trễ 10 phút lần đầu để không chạy ngay lúc khởi động
    @Scheduled(fixedDelayString = "${app.executor.invitation-expiry-scan-delay:PT1H}",
            initialDelayString = "${app.executor.invitation-expiry-initial-delay:PT10M}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        remindSoonToExpire(now);
        expireOverdue(now);
    }

    /** Nhắc một lần khi lời mời còn không quá reminderDays ngày. */
    public void remindSoonToExpire(LocalDateTime now) {
        List<UUID> ids = executorAssignmentRepository.findInvitationIdsToRemind(
                now, now.plusDays(policy.reminderDays()), PageRequest.of(0, BATCH_SIZE));
        for (UUID id : ids) {
            run(id, "remind", status -> remind(id, now, status));
        }
    }

    /** Thu hồi lời mời đã quá hạn. */
    public void expireOverdue(LocalDateTime now) {
        List<UUID> ids = executorAssignmentRepository.findExpiredInvitationIds(now, PageRequest.of(0, BATCH_SIZE));
        for (UUID id : ids) {
            run(id, "expire", status -> expire(id, now, status));
        }
    }

    private void run(UUID id, String what, java.util.function.Function<TransactionStatus, Boolean> work) {
        try {
            requiresNewTx.execute(work::apply);
        } catch (RuntimeException e) {
            log.error("Executor invitation: could not {} invitation {}: {}", what, id, e.getMessage());
        }
    }

    private boolean remind(UUID id, LocalDateTime now, TransactionStatus status) {
        ExecutorAssignment a = executorAssignmentRepository.findById(id).orElse(null);
        // Vừa trả lời / vừa bị thu hồi / vừa được nhắc ở lần quét trước: bỏ qua
        if (a == null || !policy.isOpen(a, now) || a.getInvitationReminderSentAt() != null) {
            status.setRollbackOnly();
            return false;
        }
        a.setInvitationReminderSentAt(now);
        executorAssignmentRepository.saveAndFlush(a);
        publish(ExecutorInvitationTimeoutEvent.Kind.REMINDER, a,
                policy.daysLeft(a.getInvitationExpiresAt(), now));
        return true;
    }

    private boolean expire(UUID id, LocalDateTime now, TransactionStatus status) {
        ExecutorAssignment a = executorAssignmentRepository.findById(id).orElse(null);
        if (a == null || !policy.isExpired(a, now)) {
            status.setRollbackOnly();
            return false;
        }
        // Giống từ chối: REVOKED để mọi chỗ đang xét "!= REVOKED" tự bỏ qua, Owner chỉ định người khác được ngay.
        a.setStatus(ExecutorStatus.REVOKED);
        a.setRevokedAt(now);
        a.setResponseStatus(ExecutorResponseStatus.EXPIRED);
        a.setRespondedAt(now);
        // Khóa User trước khi đụng roles (cùng thứ tự với FR-06: User -> ExecutorAssignment)
        User executor = userRepository.findByIdForUpdate(a.getExecutorUser().getId())
                .orElse(a.getExecutorUser());
        executorRoleManager.revokeIfUnused(executor, a.getId());
        executorAssignmentRepository.saveAndFlush(a);

        auditLogService.log(AuditAction.EXECUTOR_INVITATION_EXPIRED, AuditResult.SUCCESS, null, null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, a.getId().toString(),
                "ownerId=" + a.getOwner().getId() + ", executorType=" + a.getExecutorType()
                        + ", executorUserId=" + executor.getId()
                        + ", expiryDays=" + policy.expiryDays());
        publish(ExecutorInvitationTimeoutEvent.Kind.EXPIRED, a, policy.expiryDays());
        return true;
    }

    private void publish(ExecutorInvitationTimeoutEvent.Kind kind, ExecutorAssignment a, int days) {
        User owner = a.getOwner();
        User executor = a.getExecutorUser();
        eventPublisher.publishEvent(new ExecutorInvitationTimeoutEvent(kind, a.getId(), owner.getId(),
                owner.getEmail(), owner.getFullName(), executor.getEmail(), executor.getFullName(),
                a.getExecutorType(), days));
    }
}
