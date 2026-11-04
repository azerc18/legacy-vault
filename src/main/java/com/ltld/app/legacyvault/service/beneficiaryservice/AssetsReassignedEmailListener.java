package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.event.AssetsReassignedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * N2-notify: gửi email cho người nhận thay sau khi giao dịch chuyển phân bổ đã commit.
 * Mỗi người một try/catch: một email lỗi không chặn người khác và không ảnh hưởng việc đã chuyển.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetsReassignedEmailListener {

    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssetsReassigned(AssetsReassignedEvent event) {
        boolean noResponse = event.reason() == AssetsReassignedEvent.Reason.NO_RESPONSE;
        for (AssetsReassignedEvent.Receiver receiver : event.receivers()) {
            try {
                userRepository.findById(receiver.userId()).ifPresent(user -> {
                    if (user.getEmail() == null || user.getEmail().isBlank()) {
                        return;
                    }
                    emailSender.sendAssetsReassignedNotice(user.getEmail(), user.getFullName(),
                            event.ownerName(), event.vaultName(), receiver.assetCount(), noResponse, event.vaultId());
                });
            } catch (RuntimeException e) {
                log.warn("N2-notify: vault {} - reassigned-assets email for user {} not sent: {}",
                        event.vaultId(), receiver.userId(), e.getClass().getSimpleName(), e);
            }
        }
    }
}