package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.stream.Collectors;

/**
 * F5 (B4): khi Legal Verifier ký mở khóa (FR-14 phát VaultsUnlockedEvent), báo cho Executor đã nộp hồ sơ.
 *
 * <p>Listener riêng, không đụng BeneficiaryNotificationListener (FR-16). Giống ExecutorAssignmentMailListener:
 * AFTER_COMMIT nên rollback thì không gửi, @Async để không giữ request của Verifier; lỗi gửi chỉ log warn,
 * không retry. Dữ liệu đọc trong transaction read-only riêng, mail gửi ngoài transaction để không giữ kết nối DB.
 */
@Slf4j
@Component
public class ExecutorUnlockNotificationListener {

    // Tham số: Owner, danh sách tên Vault
    private static final String MSG_UNLOCKED =
            "The verification request for %s has been signed by a Legal Verifier. The following vaults have been unlocked: %s.%n"
                    + "The system will notify the beneficiaries. Please sign in to LegacyVault to follow "
                    + "the handover process.";

    private final LegalVerificationRequestRepository requestRepository;
    private final VaultRepository vaultRepository;
    private final ExecutorNotificationService executorNotificationService;
    private final TransactionTemplate readOnlyTx;

    public ExecutorUnlockNotificationListener(LegalVerificationRequestRepository requestRepository,
                                              VaultRepository vaultRepository,
                                              ExecutorNotificationService executorNotificationService,
                                              PlatformTransactionManager transactionManager) {
        this.requestRepository = requestRepository;
        this.vaultRepository = vaultRepository;
        this.executorNotificationService = executorNotificationService;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        // REQUIRES_NEW: nếu listener chạy đồng bộ (không @Async) thì không nhập vào transaction vừa commit.
        this.readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    record UnlockMail(String executorEmail, String content) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        if (event.vaultIds() == null || event.vaultIds().isEmpty()) {
            return;
        }
        UnlockMail mail = readOnlyTx.execute(status -> prepare(event));
        if (mail == null) {
            return;
        }
        try {
            executorNotificationService.send(mail.executorEmail(), mail.content());
        } catch (RuntimeException e) {
            log.warn("F5: sending unlock notice for request {} failed: {}", event.requestId(), e.getMessage());
        }
    }

    private UnlockMail prepare(VaultsUnlockedEvent event) {
        LegalVerificationRequest request = requestRepository.findById(event.requestId()).orElse(null);
        if (request == null) {
            log.warn("F5: verification request {} not found, skipping executor unlock notice", event.requestId());
            return null;
        }
        String vaultNames = vaultRepository.findAllById(event.vaultIds()).stream()
                .map(v -> "\"" + v.getName() + "\"")
                .collect(Collectors.joining(", "));
        return new UnlockMail(request.getExecutor().getEmail(),
                String.format(MSG_UNLOCKED, ownerLabel(request.getOwner()), vaultNames));
    }

    private static String ownerLabel(User owner) {
        return owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail()
                : owner.getFullName() + " (" + owner.getEmail() + ")";
    }
}
