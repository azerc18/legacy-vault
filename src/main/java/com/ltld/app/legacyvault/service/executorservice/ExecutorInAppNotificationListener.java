package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.event.VerificationRequestDecidedEvent;
import com.ltld.app.legacyvault.event.VerifierReplyPostedEvent;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * B7: lưu thông báo trong app (IN_APP) cho Executor khi Vault mở khóa, Verifier trả lời hoặc hồ sơ được quyết định.
 *
 * <p>Tách khỏi các listener gửi mail: mail lỗi thì bản ghi trong app vẫn còn, Executor vẫn thấy trên giao diện.
 * AFTER_COMMIT + @Async như các listener khác; ghi trong transaction REQUIRES_NEW riêng, lỗi chỉ log warn.
 */
@Slf4j
@Component
public class ExecutorInAppNotificationListener {

    static final String ENTITY_REQUEST = "LegalVerificationRequest";

    private static final String MSG_UNLOCKED =
            "Your verification request for %s has been signed. The vaults are unlocked for the beneficiaries.";
    private static final String MSG_REPLY = "The Legal Verifier replied to your verification request for %s.";
    private static final String MSG_APPROVED =
            "Your verification request for %s has been approved and is waiting for the Legal Verifier's signature.";
    private static final String MSG_REJECTED =
            "Your verification request for %s has been rejected. Reason: %s. You can submit a new request.";

    private final LegalVerificationRequestRepository requestRepository;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate writeTx;

    public ExecutorInAppNotificationListener(LegalVerificationRequestRepository requestRepository,
                                             NotificationRepository notificationRepository,
                                             PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.notificationRepository = notificationRepository;
        this.writeTx = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW: nếu listener chạy đồng bộ (không @Async) thì không nhập vào transaction vừa commit.
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        record(event.requestId(), NotificationType.VAULT_UNLOCKED, MSG_UNLOCKED, null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerifierReply(VerifierReplyPostedEvent event) {
        record(event.requestId(), NotificationType.VERIFIER_REPLY, MSG_REPLY, null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(VerificationRequestDecidedEvent event) {
        if (event.status() == LegalVerificationStatus.APPROVED) {
            record(event.requestId(), NotificationType.VERIFICATION_DECIDED, MSG_APPROVED, null);
        } else if (event.status() == LegalVerificationStatus.REJECTED) {
            String reason = event.reason() == null || event.reason().isBlank() ? "Not specified" : event.reason();
            record(event.requestId(), NotificationType.VERIFICATION_DECIDED, MSG_REJECTED, reason);
        }
    }

    private void record(UUID requestId, NotificationType type, String template, String reason) {
        try {
            writeTx.executeWithoutResult(status -> {
                LegalVerificationRequest request = requestRepository.findById(requestId).orElse(null);
                if (request == null) {
                    log.warn("B7: verification request {} not found, skipping {} notification", requestId, type);
                    return;
                }
                String owner = ownerLabel(request.getOwner());
                notificationRepository.save(Notification.builder()
                        .recipient(request.getExecutor())
                        .type(type)
                        .channel(NotificationChannel.IN_APP)
                        .referenceType(ENTITY_REQUEST)
                        .referenceId(requestId)
                        .content(reason == null ? String.format(template, owner) : String.format(template, owner, reason))
                        .status(NotificationStatus.SENT)
                        .sentAt(LocalDateTime.now())
                        .build());
            });
        } catch (RuntimeException e) {
            log.warn("B7: saving {} notification for request {} failed: {}", type, requestId, e.getMessage());
        }
    }

    private static String ownerLabel(User owner) {
        return owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail()
                : owner.getFullName() + " (" + owner.getEmail() + ")";
    }
}
