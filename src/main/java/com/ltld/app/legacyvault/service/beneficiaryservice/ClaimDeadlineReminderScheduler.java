package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * N25: tự nhắc người thụ hưởng chưa nhận xong khi két còn 7 ngày và 1 ngày tới hạn.
 * shortcut: chạy mỗi ngày một lần và lọc hạn trong cửa sổ đúng 24 giờ nên không cần cột đánh dấu; app tắt đúng giờ
 * chạy thì bỏ lỡ một lần nhắc. Cần chắc chắn hơn thì lưu lần nhắc cuối theo từng mốc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.beneficiary.claim-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ClaimDeadlineReminderScheduler {

    static final List<Integer> REMINDER_DAYS = List.of(7, 1);

    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final EmailSender emailSender;

    @Scheduled(cron = "${app.beneficiary.claim-deadline-reminder-cron:0 0 9 * * *}")
    public void runDaily() {
        sendDue(LocalDateTime.now());
    }

    public void sendDue(LocalDateTime now) {
        for (int days : REMINDER_DAYS) {
            for (VaultBeneficiary b : vaultBeneficiaryRepository.findUnclaimedWithDeadlineBetween(
                    now.plusDays(days - 1L), now.plusDays(days))) {
                remind(b, days);
            }
        }
    }

    private void remind(VaultBeneficiary b, int days) {
        Vault vault = b.getVault();
        User owner = vault.getOwner();
        String ownerName = owner.getFullName() == null || owner.getFullName().isBlank()
                ? owner.getEmail() : owner.getFullName();
        try {
            emailSender.sendClaimDeadlineReminder(b.getEmail(), b.getFullName(), ownerName, vault.getName(),
                    vault.getClaimDeadlineAt(), days, vault.getId());
        } catch (RuntimeException e) {
            log.warn("N25: reminder for vault beneficiary {} failed: {}", b.getId(), e.getMessage());
        }
    }
}
