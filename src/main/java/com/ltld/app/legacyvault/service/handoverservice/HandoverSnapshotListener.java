package com.ltld.app.legacyvault.service.handoverservice;

import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * C.6: Vault mở khóa thì chụp lại trạng thái tài sản + xác thực. AFTER_COMMIT + @Async như các listener
 * VaultsUnlockedEvent khác; lỗi chỉ log vì dossier / ensureSnapshot sẽ tạo bù khi cần.
 */
@Slf4j
@Component
public class HandoverSnapshotListener {

    private final HandoverSnapshotService handoverSnapshotService;

    public HandoverSnapshotListener(HandoverSnapshotService handoverSnapshotService) {
        this.handoverSnapshotService = handoverSnapshotService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        try {
            handoverSnapshotService.createForVaults(event.vaultIds());
        } catch (RuntimeException e) {
            log.warn("C.6: handover snapshot for request {} failed: {}", event.requestId(), e.getMessage());
        }
    }
}
