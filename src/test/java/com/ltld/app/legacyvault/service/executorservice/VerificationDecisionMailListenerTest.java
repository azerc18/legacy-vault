package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.event.VerificationRequestDecidedEvent;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationDecisionMailListenerTest {

    @Mock private LegalVerificationRequestRepository requestRepository;
    @Mock private ExecutorNotificationService executorNotificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private VerificationDecisionMailListener listener;
    private UUID requestId;
    private LegalVerificationRequest request;

    @BeforeEach
    void setUp() {
        listener = new VerificationDecisionMailListener(requestRepository, executorNotificationService,
                transactionManager);
        requestId = UUID.randomUUID();
        request = LegalVerificationRequest.builder().id(requestId)
                .owner(User.builder().fullName("Nguyễn Văn A").email("owner@example.test").build())
                .executor(User.builder().email("executor@example.test").build())
                .build();
    }

    private String sentContent() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(executorNotificationService).send(eq("executor@example.test"), content.capture());
        return content.getValue();
    }

    @Test
    @DisplayName("B1: REJECTED -> mail Executor kèm lý do và hướng dẫn nộp hồ sơ mới")
    void rejected_mailsExecutorWithReasonAndResubmitHint() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.REJECTED,
                "Document unreadable: blurry scan"));

        assertThat(sentContent())
                .contains("Nguyễn Văn A (owner@example.test)")
                .contains("rejected")
                .contains("Reason: Document unreadable: blurry scan")
                .contains("submit a new verification request")
                .contains(requestId.toString());
    }

    @Test
    @DisplayName("B1: REJECTED không có lý do -> ghi 'Not specified', không in 'null'")
    void rejectedWithoutReason_doesNotPrintNull() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.REJECTED, null));

        assertThat(sentContent()).contains("Reason: Not specified").doesNotContain("null");
    }

    @Test
    @DisplayName("B1: APPROVED -> mail Executor báo đã duyệt, chờ ký")
    void approved_mailsExecutor() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.APPROVED, null));

        assertThat(sentContent()).contains("approved").doesNotContain("Reason:");
    }

    @Test
    @DisplayName("B1: Owner không có tên -> dùng email Owner")
    void ownerWithoutName_usesEmail() {
        request.getOwner().setFullName(null);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.APPROVED, null));

        assertThat(sentContent()).contains("request for owner@example.test has");
    }

    @Test
    @DisplayName("B1: trạng thái khác APPROVED/REJECTED -> bỏ qua, không đọc DB")
    void otherStatus_isIgnored() {
        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.SIGNED, null));

        verifyNoInteractions(requestRepository, executorNotificationService);
    }

    @Test
    @DisplayName("B1: không tìm thấy hồ sơ -> không gửi, không lỗi")
    void requestNotFound_sendsNothing() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.REJECTED, "x"));

        verifyNoInteractions(executorNotificationService);
    }

    @Test
    @DisplayName("B1: gửi mail lỗi -> nuốt lỗi, không ném ra")
    void sendFailure_isSwallowed() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        doThrow(new MailSendException("SMTP down")).when(executorNotificationService).send(anyString(), anyString());

        assertThatCode(() -> listener.onDecided(new VerificationRequestDecidedEvent(
                requestId, LegalVerificationStatus.REJECTED, "x"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("B1: đọc dữ liệu trong transaction read-only REQUIRES_NEW")
    void readsInReadOnlyRequiresNewTransaction() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        listener.onDecided(new VerificationRequestDecidedEvent(requestId, LegalVerificationStatus.APPROVED, null));

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("B1: listener chạy @Async và AFTER_COMMIT")
    void listenerIsAsyncAfterCommit() throws NoSuchMethodException {
        var method = VerificationDecisionMailListener.class.getMethod("onDecided",
                VerificationRequestDecidedEvent.class);

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
