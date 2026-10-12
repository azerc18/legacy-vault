package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidAttachmentException;
import com.ltld.app.legacyvault.exception.VerificationNotFoundException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationExchangeServiceImplTest {

    @Mock private VerificationExchangeMessageRepository messageRepository;
    @Mock private IdentityVerificationRepository verificationRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private VerificationExchangeServiceImpl service;

    private User owner;
    private User executor;
    private Vault vault;
    private IdentityVerification verification;
    private ExecutorAssignment assignment;
    private UUID verificationId;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).email("owner@test.com").fullName("Owner User").build();
        executor = User.builder().id(UUID.randomUUID()).email("executor@test.com").fullName("Executor User").build();

        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("My Vault").build();

        verificationId = UUID.randomUUID();
        verification = IdentityVerification.builder()
                .id(verificationId)
                .vault(vault)
                .beneficiary(owner)
                .method(VerificationMethod.OTP)
                .status(VerificationStatus.PENDING)
                .build();

        assignment = ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(verificationRepository.findById(verificationId)).thenReturn(Optional.of(verification));
        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId())).thenReturn(List.of(assignment));
    }

    @Test
    @DisplayName("Happy path: Gửi tin nhắn thành công")
    void sendExecutorMessage_happyPath_success() {
        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Xin chào, tôi gửi bổ sung giấy chứng tử")
                .attachmentUrl("https://storage.vault.local/cert.pdf")
                .attachmentName("cert.pdf")
                .attachmentSize(1024L)
                .build();

        when(messageRepository.save(any(VerificationExchangeMessage.class))).thenAnswer(inv -> {
            VerificationExchangeMessage msg = inv.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });

        VerificationMessageResponse response = service.sendExecutorMessage(verificationId, request, executor.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getSenderRole()).isEqualTo(ExchangeSenderRole.EXECUTOR);
        assertThat(response.getMessageContent()).isEqualTo("Xin chào, tôi gửi bổ sung giấy chứng tử");
        assertThat(response.getAttachmentName()).isEqualTo("cert.pdf");
        assertThat(response.getAttachmentSize()).isEqualTo(1024L);

        ArgumentCaptor<VerificationExchangeMessage> captor = ArgumentCaptor.forClass(VerificationExchangeMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getSender().getEmail()).isEqualTo(executor.getEmail());
    }

    @Test
    @DisplayName("Happy path: Lấy lịch sử tin nhắn trao đổi thành công")
    void getExchangeMessages_happyPath_success() {
        VerificationExchangeMessage msg1 = VerificationExchangeMessage.builder()
                .id(UUID.randomUUID())
                .verification(verification)
                .sender(executor)
                .senderRole(ExchangeSenderRole.EXECUTOR)
                .messageContent("Tin nhắn từ Executor")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        when(messageRepository.findAllByVerificationIdOrderByCreatedAtAsc(verificationId))
                .thenReturn(List.of(msg1));

        List<VerificationMessageResponse> list = service.getExchangeMessages(verificationId, executor.getEmail());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getMessageContent()).isEqualTo("Tin nhắn từ Executor");
        assertThat(list.get(0).getSenderRole()).isEqualTo(ExchangeSenderRole.EXECUTOR);
    }

    @Test
    @DisplayName("Edge case: Không tìm thấy IdentityVerification -> 404")
    void sendExecutorMessage_verificationNotFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(verificationRepository.findById(unknownId)).thenReturn(Optional.empty());

        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Hello")
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(unknownId, request, executor.getEmail()))
                .isInstanceOf(VerificationNotFoundException.class)
                .hasMessageContaining("Identity verification request not found.");
    }

    @Test
    @DisplayName("Edge case: Executor không được phân công quản lý Vault liên quan -> 404")
    void sendExecutorMessage_notAssigned_throwsException() {
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId())).thenReturn(Collections.emptyList());

        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Hello")
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(verificationId, request, executor.getEmail()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class)
                .hasMessageContaining("Executor not assigned to this vault.");
    }

    @Test
    @DisplayName("Edge case: Executor chưa ACTIVE -> 403")
    void sendExecutorMessage_executorNotActive_throwsException() {
        assignment.setStatus(ExecutorStatus.INACTIVE);

        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Hello")
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(verificationId, request, executor.getEmail()))
                .isInstanceOf(ExecutorNotActiveException.class)
                .hasMessageContaining("Access denied. Executor has not been activated yet.");
    }

    @Test
    @DisplayName("Edge case: Tin nhắn rỗng và không có file đính kèm -> 400")
    void sendExecutorMessage_emptyMessageAndNoAttachment_throwsException() {
        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("   ")
                .attachmentUrl("")
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(verificationId, request, executor.getEmail()))
                .isInstanceOf(InvalidAttachmentException.class)
                .hasMessageContaining("Message content or attachment must not be empty.");
    }

    @Test
    @DisplayName("Edge case: File đính kèm vượt quá 10MB -> 400")
    void sendExecutorMessage_attachmentTooLarge_throwsException() {
        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("File to")
                .attachmentUrl("https://example.com/big.pdf")
                .attachmentSize(15L * 1024 * 1024) // 15MB
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(verificationId, request, executor.getEmail()))
                .isInstanceOf(InvalidAttachmentException.class)
                .hasMessageContaining("Attachment size exceeds the maximum allowed limit");
    }

    @Test
    @DisplayName("Edge case: File đính kèm có size âm -> 400")
    void sendExecutorMessage_negativeSize_throwsException() {
        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Negative size")
                .attachmentUrl("https://example.com/test.pdf")
                .attachmentSize(-5L)
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(verificationId, request, executor.getEmail()))
                .isInstanceOf(InvalidAttachmentException.class)
                .hasMessageContaining("Invalid attachment size.");
    }

    @Test
    @DisplayName("Edge case: Lịch sử tin nhắn rỗng -> trả về empty list")
    void getExchangeMessages_emptyList_returnsEmpty() {
        when(messageRepository.findAllByVerificationIdOrderByCreatedAtAsc(verificationId))
                .thenReturn(Collections.emptyList());

        List<VerificationMessageResponse> list = service.getExchangeMessages(verificationId, executor.getEmail());

        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("Edge case: URL tệp đính kèm không hợp lệ (không phải http/https) -> 400")
    void sendExecutorMessage_invalidAttachmentUrl_throwsException() {
        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Gửi file xác minh")
                .attachmentUrl("ftp://storage.vault.local/cert.pdf")
                .attachmentSize(1024L)
                .build();

        assertThatThrownBy(() -> service.sendExecutorMessage(verificationId, request, executor.getEmail()))
                .isInstanceOf(InvalidAttachmentException.class)
                .hasMessageContaining("Invalid attachment URL.");
    }
}
