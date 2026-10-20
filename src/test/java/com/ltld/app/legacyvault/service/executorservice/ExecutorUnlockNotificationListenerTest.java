package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
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
class ExecutorUnlockNotificationListenerTest {

    @Mock private LegalVerificationRequestRepository requestRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private ExecutorNotificationService executorNotificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private ExecutorUnlockNotificationListener listener;
    private UUID requestId;
    private UUID vaultId;
    private LegalVerificationRequest request;

    @BeforeEach
    void setUp() {
        listener = new ExecutorUnlockNotificationListener(requestRepository, vaultRepository,
                executorNotificationService, transactionManager);
        requestId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        request = LegalVerificationRequest.builder().id(requestId)
                .owner(User.builder().fullName("Nguyễn Văn A").email("owner@example.test").build())
                .executor(User.builder().email("executor@example.test").build())
                .build();
    }

    @Test
    @DisplayName("B4: Vault mở khóa -> gửi mail cho Executor của hồ sơ, có tên Owner và tên Vault")
    void unlock_sendsNoticeToRequestExecutor() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(vaultRepository.findAllById(List.of(vaultId)))
                .thenReturn(List.of(Vault.builder().id(vaultId).name("Tài khoản ngân hàng").build()));

        listener.onVaultsUnlocked(new VaultsUnlockedEvent(requestId, List.of(vaultId)));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(executorNotificationService).send(eq("executor@example.test"), content.capture());
        assertThat(content.getValue())
                .contains("Nguyễn Văn A (owner@example.test)")
                .contains("\"Tài khoản ngân hàng\"");
    }

    @Test
    @DisplayName("B4: Owner không có tên -> dùng email Owner")
    void ownerWithoutName_usesEmail() {
        request.getOwner().setFullName(" ");
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(vaultRepository.findAllById(List.of(vaultId)))
                .thenReturn(List.of(Vault.builder().id(vaultId).name("V").build()));

        listener.onVaultsUnlocked(new VaultsUnlockedEvent(requestId, List.of(vaultId)));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(executorNotificationService).send(eq("executor@example.test"), content.capture());
        assertThat(content.getValue()).contains("Hồ sơ xác minh của owner@example.test đã");
    }

    @Test
    @DisplayName("B4: không tìm thấy hồ sơ -> không gửi, không lỗi")
    void requestNotFound_sendsNothing() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        listener.onVaultsUnlocked(new VaultsUnlockedEvent(requestId, List.of(vaultId)));

        verifyNoInteractions(executorNotificationService);
    }

    @Test
    @DisplayName("B4: danh sách Vault rỗng -> bỏ qua, không đọc DB")
    void emptyVaultList_isIgnored() {
        listener.onVaultsUnlocked(new VaultsUnlockedEvent(requestId, List.of()));

        verifyNoInteractions(requestRepository, vaultRepository, executorNotificationService);
    }

    @Test
    @DisplayName("B4: gửi mail lỗi -> nuốt lỗi, không ném ra")
    void sendFailure_isSwallowed() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(vaultRepository.findAllById(List.of(vaultId)))
                .thenReturn(List.of(Vault.builder().id(vaultId).name("V").build()));
        doThrow(new MailSendException("SMTP down")).when(executorNotificationService).send(anyString(), anyString());

        assertThatCode(() -> listener.onVaultsUnlocked(new VaultsUnlockedEvent(requestId, List.of(vaultId))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("B4: listener chạy @Async và AFTER_COMMIT")
    void listenerIsAsyncAfterCommit() throws NoSuchMethodException {
        var method = ExecutorUnlockNotificationListener.class.getMethod("onVaultsUnlocked", VaultsUnlockedEvent.class);

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
