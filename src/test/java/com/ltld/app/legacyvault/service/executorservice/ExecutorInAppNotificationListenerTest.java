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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorInAppNotificationListenerTest {

    @Mock private LegalVerificationRequestRepository requestRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private ExecutorInAppNotificationListener listener;
    private UUID requestId;
    private User executor;

    @BeforeEach
    void setUp() {
        listener = new ExecutorInAppNotificationListener(requestRepository, notificationRepository, transactionManager);
        requestId = UUID.randomUUID();
        executor = User.builder().id(UUID.randomUUID()).email("executor@example.test").build();
    }

    private void stubRequest() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(LegalVerificationRequest.builder()
                .id(requestId).executor(executor)
                .owner(User.builder().fullName("Nguyễn Văn A").email("owner@example.test").build())
                .build()));
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getRecipient()).isSameAs(executor);
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getReferenceType()).isEqualTo("LegalVerificationRequest");
        assertThat(n.getReferenceId()).isEqualTo(requestId);
        return n;
    }

    @Test
    void vaultsUnlocked_savesInAppNotificationForExecutor() {
        stubRequest();
        listener.onVaultsUnlocked(new VaultsUnlockedEvent(requestId, List.of(UUID.randomUUID())));
        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.VAULT_UNLOCKED);
        assertThat(n.getContent()).contains("Nguyễn Văn A (owner@example.test)").contains("unlocked");
    }

    @Test
    void verifierReply_savesInAppNotification() {
        stubRequest();
        listener.onVerifierReply(new VerifierReplyPostedEvent(requestId, executor.getId()));
        assertThat(saved().getType()).isEqualTo(NotificationType.VERIFIER_REPLY);
    }

    @Test
    void rejected_savesNotificationWithReason() {
        stubRequest();
        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.REJECTED,
                "Document unreadable"));
        Notification n = saved();
        assertThat(n.getType()).isEqualTo(NotificationType.VERIFICATION_DECIDED);
        assertThat(n.getContent()).contains("rejected").contains("Reason: Document unreadable");
    }

    @Test
    void rejectedWithoutReason_doesNotPrintNull() {
        stubRequest();
        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.REJECTED, null));
        assertThat(saved().getContent()).contains("Not specified").doesNotContain("null");
    }

    @Test
    void approved_savesNotification() {
        stubRequest();
        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.APPROVED, null));
        assertThat(saved().getContent()).contains("approved");
    }

    @Test
    void otherDecisionStatus_isIgnored() {
        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.SIGNED, null));
        verifyNoInteractions(requestRepository, notificationRepository, transactionManager);
    }

    @Test
    void requestNotFound_savesNothing() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());
        listener.onVerifierReply(new VerifierReplyPostedEvent(requestId, executor.getId()));
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void saveFailure_isSwallowed() {
        stubRequest();
        when(notificationRepository.save(any())).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> listener.onVerifierReply(new VerifierReplyPostedEvent(requestId, executor.getId())))
                .doesNotThrowAnyException();
    }

    @Test
    void writesInRequiresNewTransaction() {
        stubRequest();
        listener.onVerifierReply(new VerifierReplyPostedEvent(requestId, executor.getId()));
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isFalse();
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void handlersAreAsyncAfterCommit() {
        for (Method method : ExecutorInAppNotificationListener.class.getDeclaredMethods()) {
            if (method.getName().startsWith("on")) {
                assertThat(method.isAnnotationPresent(Async.class)).as(method.getName()).isTrue();
                assertThat(method.getAnnotation(TransactionalEventListener.class).phase()).as(method.getName())
                        .isEqualTo(TransactionPhase.AFTER_COMMIT);
            }
        }
    }
}
