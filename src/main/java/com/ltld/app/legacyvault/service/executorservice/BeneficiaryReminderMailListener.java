package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.event.BeneficiaryReminderRequestedEvent;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * N2-exec: gửi mail nhắc người thụ hưởng. AFTER_COMMIT nên rollback thì không gửi; bản thân
 * EmailSender đã @Async nên request của Executor không phải chờ. Lỗi gửi chỉ log, lượt nhắc 24h vẫn tính.
 */
@Slf4j
@Component
public class BeneficiaryReminderMailListener {

    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final EmailSender emailSender;
    private final TransactionTemplate readOnlyTx;

    public BeneficiaryReminderMailListener(VaultBeneficiaryRepository vaultBeneficiaryRepository,
                                           EmailSender emailSender,
                                           PlatformTransactionManager transactionManager) {
        this.vaultBeneficiaryRepository = vaultBeneficiaryRepository;
        this.emailSender = emailSender;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        // Listener chạy ngay sau commit trên cùng thread: REQUIRES_NEW để không nhập vào transaction vừa xong
        this.readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    record ReminderMail(String email, String fullName, String ownerName, String vaultName,
                        LocalDateTime claimDeadlineAt, UUID vaultId) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReminder(BeneficiaryReminderRequestedEvent event) {
        ReminderMail mail = readOnlyTx.execute(status -> prepare(event.vaultBeneficiaryId()));
        if (mail == null) {
            return;
        }
        try {
            emailSender.sendBeneficiaryClaimReminder(mail.email(), mail.fullName(), mail.ownerName(),
                    mail.vaultName(), mail.claimDeadlineAt(), mail.vaultId());
        } catch (RuntimeException e) {
            log.warn("N2-exec: reminder for vault beneficiary {} failed: {}", event.vaultBeneficiaryId(),
                    e.getMessage());
        }
    }

    private ReminderMail prepare(UUID vaultBeneficiaryId) {
        VaultBeneficiary vb = vaultBeneficiaryRepository.findById(vaultBeneficiaryId).orElse(null);
        if (vb == null) {
            log.warn("N2-exec: vault beneficiary {} not found, skipping reminder", vaultBeneficiaryId);
            return null;
        }
        Vault vault = vb.getVault();
        User owner = vault.getOwner();
        String ownerName = owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail() : owner.getFullName();
        return new ReminderMail(vb.getEmail(), vb.getFullName(), ownerName, vault.getName(),
                vault.getClaimDeadlineAt(), vault.getId());
    }
}
