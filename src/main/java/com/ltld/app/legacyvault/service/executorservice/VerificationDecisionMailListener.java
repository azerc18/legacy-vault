package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.event.VerificationRequestDecidedEvent;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * B1: báo Executor khi Legal Verifier duyệt (APPROVED) hoặc từ chối (REJECTED) hồ sơ (FR-12).
 *
 * <p>Cùng mẫu với ExecutorUnlockNotificationListener: AFTER_COMMIT nên rollback thì không gửi, @Async để không
 * giữ request của Verifier; đọc dữ liệu trong transaction read-only riêng, lỗi gửi chỉ log warn.
 */
@Slf4j
@Component
public class VerificationDecisionMailListener {

    // Tham số: Owner, mã hồ sơ
    private static final String MSG_APPROVED =
            "Your verification request for %s has been approved by a Legal Verifier (request ID: %s).%n"
                    + "The vaults will be unlocked once the Legal Verifier signs the request. "
                    + "Please sign in to LegacyVault to follow the handover process.";

    // Tham số: Owner, mã hồ sơ, lý do
    private static final String MSG_REJECTED =
            "Your verification request for %s has been rejected by a Legal Verifier (request ID: %s).%n"
                    + "Reason: %s%n"
                    + "To continue, sign in to LegacyVault and submit a new verification request "
                    + "with a valid death certificate.";

    private final LegalVerificationRequestRepository requestRepository;
    private final ExecutorNotificationService executorNotificationService;
    private final TransactionTemplate readOnlyTx;

    public VerificationDecisionMailListener(LegalVerificationRequestRepository requestRepository,
                                            ExecutorNotificationService executorNotificationService,
                                            PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.executorNotificationService = executorNotificationService;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        // REQUIRES_NEW: nếu listener chạy đồng bộ (không @Async) thì không nhập vào transaction vừa commit.
        this.readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    record DecisionMail(String executorEmail, String content) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(VerificationRequestDecidedEvent event) {
        if (event.status() != LegalVerificationStatus.APPROVED && event.status() != LegalVerificationStatus.REJECTED) {
            return;
        }
        DecisionMail mail = readOnlyTx.execute(status -> prepare(event));
        if (mail == null) {
            return;
        }
        try {
            executorNotificationService.send(mail.executorEmail(), mail.content());
        } catch (RuntimeException e) {
            log.warn("B1: sending {} notice for request {} failed: {}", event.status(), event.requestId(),
                    e.getMessage());
        }
    }

    private DecisionMail prepare(VerificationRequestDecidedEvent event) {
        LegalVerificationRequest request = requestRepository.findById(event.requestId()).orElse(null);
        if (request == null) {
            log.warn("B1: verification request {} not found, skipping decision notice", event.requestId());
            return null;
        }
        String owner = ownerLabel(request.getOwner());
        String content = event.status() == LegalVerificationStatus.APPROVED
                ? String.format(MSG_APPROVED, owner, event.requestId())
                : String.format(MSG_REJECTED, owner, event.requestId(),
                        event.reason() == null || event.reason().isBlank() ? "Not specified" : event.reason());
        return new DecisionMail(request.getExecutor().getEmail(), content);
    }

    private static String ownerLabel(User owner) {
        return owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail()
                : owner.getFullName() + " (" + owner.getEmail() + ")";
    }
}
