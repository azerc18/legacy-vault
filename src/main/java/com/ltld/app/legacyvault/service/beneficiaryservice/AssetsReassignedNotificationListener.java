package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.event.AssetsReassignedEvent;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * N2-notify: thông báo in-app cho người nhận thay sau khi Decline đã commit.
 * Mỗi người một giao dịch riêng (REQUIRES_NEW) có try/catch: một thông báo lỗi không làm mất thông báo
 * của người khác, và không bao giờ làm rollback lần Decline.
 */
@Slf4j
@Component
public class AssetsReassignedNotificationListener {

    private static final String ENTITY_VAULT = "Vault";
    // Tham số: số tài sản, tên Vault, tên Owner
    static final String MSG_ASSETS_REASSIGNED =
            "%d asset(s) in the vault \"%s\" of %s have been reassigned to you because another beneficiary declined their share.";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate requiresNewTx;

    public AssetsReassignedNotificationListener(NotificationRepository notificationRepository,
                                                UserRepository userRepository,
                                                PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssetsReassigned(AssetsReassignedEvent event) {
        for (AssetsReassignedEvent.Receiver receiver : event.receivers()) {
            try {
                requiresNewTx.executeWithoutResult(status -> save(event, receiver));
            } catch (RuntimeException e) {
                log.warn("N2-notify: vault {} - in-app notification for user {} not saved: {}",
                        event.vaultId(), receiver.userId(), e.getClass().getSimpleName(), e);
            }
        }
    }

    private void save(AssetsReassignedEvent event, AssetsReassignedEvent.Receiver receiver) {
        userRepository.findById(receiver.userId()).ifPresent(user ->
                notificationRepository.save(Notification.builder()
                        .recipient(user)
                        .type(NotificationType.ASSET_REASSIGNED)
                        .channel(NotificationChannel.IN_APP)
                        .referenceType(ENTITY_VAULT)
                        .referenceId(event.vaultId())
                        .content(String.format(MSG_ASSETS_REASSIGNED,
                                receiver.assetCount(), event.vaultName(), event.ownerName()))
                        .status(NotificationStatus.SENT)
                        .sentAt(LocalDateTime.now())
                        .build()));
    }
}