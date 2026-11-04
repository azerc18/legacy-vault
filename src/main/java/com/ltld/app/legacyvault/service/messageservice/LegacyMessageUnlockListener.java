package com.ltld.app.legacyvault.service.messageservice;

import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * F4b: Khi Vault được mở khóa (Legal Verifier ký mở khóa phát VaultsUnlockedEvent),
 * tự động phát các lời nhắn di nguyện có chế độ ON_CLAIM (và các lời nhắn hẹn giờ đã đến hạn)
 * tới người thụ hưởng tương ứng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyMessageUnlockListener {

    private final LegacyMessageService legacyMessageService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        if (event == null || event.vaultIds() == null) {
            return;
        }
        for (UUID vaultId : event.vaultIds()) {
            try {
                int delivered = legacyMessageService.deliverOnClaimMessages(vaultId);
                if (delivered > 0) {
                    log.info("F4b: delivered {} legacy message(s) on unlock for vault {}", delivered, vaultId);
                }
            } catch (RuntimeException e) {
                log.warn("F4b: failed to deliver on-claim legacy messages for vault {}: {}", vaultId, e.getMessage());
            }
        }
    }
}
