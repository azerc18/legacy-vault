package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.event.ExecutorTaskDisputedEvent;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
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
import java.util.List;

/**
 * C.6: Beneficiary phản đối kết quả thì Executor đang ACTIVE của Owner nhận thông báo trong app (IN_APP).
 * Cùng khuôn với ExecutorInAppNotificationListener: AFTER_COMMIT + @Async, ghi transaction riêng, lỗi chỉ log.
 */
@Slf4j
@Component
public class ExecutorTaskInAppNotificationListener {

    static final String ENTITY_TASK = "ExecutorTask";
    private static final String MSG_DISPUTED =
            "The beneficiary disputed the result of your task for %s (%s). Reason: %s. "
                    + "Please review it and complete it again with new evidence.";

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate writeTx;

    public ExecutorTaskInAppNotificationListener(ExecutorAssignmentRepository executorAssignmentRepository,
                                                 NotificationRepository notificationRepository,
                                                 PlatformTransactionManager transactionManager) {
        this.executorAssignmentRepository = executorAssignmentRepository;
        this.notificationRepository = notificationRepository;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputed(ExecutorTaskDisputedEvent event) {
        try {
            writeTx.executeWithoutResult(status -> {
                List<ExecutorAssignment> executors =
                        executorAssignmentRepository.findActiveWithUserByOwnerIdIn(List.of(event.ownerId()));
                String platform = event.platform() == null || event.platform().isBlank() ? "-" : event.platform();
                for (ExecutorAssignment assignment : executors) {
                    notificationRepository.save(Notification.builder()
                            .recipient(assignment.getExecutorUser())
                            .type(NotificationType.EXECUTOR_TASK_DISPUTED)
                            .channel(NotificationChannel.IN_APP)
                            .referenceType(ENTITY_TASK)
                            .referenceId(event.taskId())
                            .content(String.format(MSG_DISPUTED, event.vaultName(), platform, event.reason()))
                            .status(NotificationStatus.SENT)
                            .sentAt(LocalDateTime.now())
                            .build());
                }
            });
        } catch (RuntimeException e) {
            log.warn("C.6 in-app: saving dispute notification for task {} failed: {}", event.taskId(), e.getMessage());
        }
    }
}
