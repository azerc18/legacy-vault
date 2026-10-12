package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.event.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.event.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ExecutorAssignmentMailListenerTest {

    @Mock private EmailSender emailSender;

    @InjectMocks
    private ExecutorAssignmentMailListener listener;

    @Test
    @DisplayName("ASSIGNED gọi sendExecutorAssignedNotice, REVOKED gọi sendExecutorRevokedNotice")
    void dispatchesEachMailToMatchingMethod() {
        listener.on(new ExecutorAssignmentChangedEvent(List.of(
                ExecutorMail.assigned("new@example.test", "Owner A", "owner@example.test", ExecutorType.PRIMARY),
                ExecutorMail.revoked("old@example.test", "Owner A", "owner@example.test", ExecutorType.PRIMARY))));

        verify(emailSender).sendExecutorAssignedNotice(
                "new@example.test", "Owner A", "owner@example.test", ExecutorType.PRIMARY);
        verify(emailSender).sendExecutorRevokedNotice(
                "old@example.test", "Owner A", "owner@example.test", ExecutorType.PRIMARY);
        verify(emailSender, never()).sendExecutorRevokedNotice(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq(ExecutorType.BACKUP));
    }

    @Test
    @DisplayName("Lỗi gửi mail bị nuốt (không ném ra) và không chặn các mail còn lại")
    void sendFailure_isSwallowedAndOtherMailsStillSent() {
        doThrow(new MailSendException("SMTP down")).when(emailSender).sendExecutorAssignedNotice(
                "new@example.test", "Owner A", "owner@example.test", ExecutorType.BACKUP);

        assertThatCode(() -> listener.on(new ExecutorAssignmentChangedEvent(List.of(
                ExecutorMail.assigned("new@example.test", "Owner A", "owner@example.test", ExecutorType.BACKUP),
                ExecutorMail.revoked("old@example.test", "Owner A", "owner@example.test", ExecutorType.BACKUP)))))
                .doesNotThrowAnyException();

        verify(emailSender).sendExecutorRevokedNotice(
                "old@example.test", "Owner A", "owner@example.test", ExecutorType.BACKUP);
    }

    @Test
    @DisplayName("Event rỗng: không gửi gì")
    void emptyEvent_sendsNothing() {
        listener.on(new ExecutorAssignmentChangedEvent(List.of()));

        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Listener chỉ chạy AFTER_COMMIT (rollback thì không gửi mail)")
    void listensAfterCommitOnly() throws NoSuchMethodException {
        TransactionalEventListener annotation = ExecutorAssignmentMailListener.class
                .getMethod("on", ExecutorAssignmentChangedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }
}
