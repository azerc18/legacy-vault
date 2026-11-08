package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminNotificationResponse;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * B1: Admin xem email thông báo gửi lỗi và gửi lại thủ công.
 * Gửi lại theo 3 bước để KHÔNG gửi mail trong transaction và không gửi trùng khi bấm 2 lần / job tự gửi lại cùng lúc:
 *   (1) "giữ chỗ" bằng UPDATE có điều kiện (status FAILED, lần thử gần nhất cách đây > 1 phút), tăng attemptCount
 *   (2) gửi mail ngoài transaction, dùng đúng nội dung đã lưu
 *   (3) cập nhật SENT nếu gửi được (lỗi thì giữ FAILED)
 */
@Slf4j
@Service
public class AdminNotificationService {

    static final int MAX_PAGE_SIZE = 100;
    static final long RETRY_COOLDOWN_SECONDS = 60;

    @PersistenceContext
    private EntityManager em;

    private final ExecutorNotificationService mailSender;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;

    public AdminNotificationService(ExecutorNotificationService mailSender, AuditLogService auditLogService,
                                    PlatformTransactionManager transactionManager) {
        this.mailSender = mailSender;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> list(NotificationStatus status, int page, int size) {
        NotificationStatus st = status != null ? status : NotificationStatus.FAILED;
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Long total = em.createQuery("""
                        SELECT COUNT(n) FROM Notification n WHERE n.channel = :ch AND n.status = :st
                        """, Long.class)
                .setParameter("ch", NotificationChannel.EMAIL).setParameter("st", st).getSingleResult();
        List<AdminNotificationResponse> rows = em.createQuery("""
                        SELECT n FROM Notification n JOIN FETCH n.recipient
                        WHERE n.channel = :ch AND n.status = :st
                        ORDER BY n.createdAt DESC, n.id DESC
                        """, Notification.class)
                .setParameter("ch", NotificationChannel.EMAIL).setParameter("st", st)
                .setFirstResult(p * s).setMaxResults(s)
                .getResultList().stream().map(AdminNotificationService::toResponse).toList();
        return new PageImpl<>(rows, PageRequest.of(p, s), total);
    }

    public AdminNotificationResponse retry(UUID adminId, UUID notificationId) {
        LocalDateTime now = LocalDateTime.now();

        // (1) giữ chỗ + đọc dữ liệu cần gửi
        Object[] target = tx.execute(status -> {
            Notification n = em.find(Notification.class, notificationId);
            if (n == null || n.getChannel() != NotificationChannel.EMAIL) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found.");
            }
            if (n.getStatus() != NotificationStatus.FAILED) {
                throw new BusinessException(ErrorCode.CONFLICT, "Only failed emails can be resent.");
            }
            // Chỉ email bàn giao cho Executor được gửi qua ExecutorNotificationService với nội dung đã lưu
            if (n.getType() != NotificationType.EXECUTOR_ACTIVATED) {
                throw new BusinessException(ErrorCode.CONFLICT, "Resending this type of email is not supported.");
            }
            if (n.getContent() == null || n.getContent().isBlank()) {
                throw new BusinessException(ErrorCode.CONFLICT, "This email has no saved content to resend.");
            }
            int claimed = em.createQuery("""
                            UPDATE Notification n SET n.attemptCount = n.attemptCount + 1, n.lastAttemptAt = :now
                            WHERE n.id = :id AND n.status = :failed
                              AND (n.lastAttemptAt IS NULL OR n.lastAttemptAt < :cooldown)
                            """)
                    .setParameter("now", now).setParameter("id", notificationId)
                    .setParameter("failed", NotificationStatus.FAILED)
                    .setParameter("cooldown", now.minusSeconds(RETRY_COOLDOWN_SECONDS))
                    .executeUpdate();
            if (claimed != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "This email was just retried. Please wait a minute.");
            }
            return new Object[]{n.getRecipient().getEmail(), n.getContent()};
        });

        // (2) gửi ngoài transaction
        boolean sent;
        try {
            mailSender.send((String) target[0], (String) target[1]);
            sent = true;
        } catch (RuntimeException e) {
            log.warn("B1: admin retry of notification {} failed: {}", notificationId, e.getMessage());
            sent = false;
        }

        // (3) cập nhật kết quả
        boolean ok = sent;
        AdminNotificationResponse result = tx.execute(status -> {
            Notification n = em.find(Notification.class, notificationId);
            if (ok) {
                n.setStatus(NotificationStatus.SENT);
                n.setSentAt(LocalDateTime.now());
            }
            return toResponse(n);
        });
        auditLogService.log(AuditAction.ADMIN_NOTIFICATION_RETRIED, ok ? AuditResult.SUCCESS : AuditResult.FAILURE,
                adminId, null, "Notification", notificationId.toString(), ok ? "resent" : "send failed");
        if (!ok) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "The email could not be sent. Please try again later.");
        }
        return result;
    }

    private static AdminNotificationResponse toResponse(Notification n) {
        return AdminNotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipient().getId())
                .recipientEmail(n.getRecipient().getEmail())
                .type(n.getType()).status(n.getStatus())
                .referenceType(n.getReferenceType()).referenceId(n.getReferenceId())
                .attemptCount(n.getAttemptCount() == null ? 0 : n.getAttemptCount())
                .lastAttemptAt(n.getLastAttemptAt()).createdAt(n.getCreatedAt()).sentAt(n.getSentAt())
                .build();
    }
}
