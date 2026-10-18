package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.event.VerifierReplyPostedEvent;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VerifierMessageServiceImplTest {

    @Mock
    private VerifierAccessGuard verifierAccessGuard;

    @Mock
    private VerificationExchangeMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UploadedDocumentValidator uploadedDocumentValidator;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private VerifierMessageServiceImpl service;

    private UUID requestId;
    private UUID verifierId;
    private UUID ownerId;
    private UUID executorId;
    private User verifier;
    private LegalVerificationRequest request;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        verifierId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        executorId = UUID.randomUUID();
        verifier = User.builder().id(verifierId).fullName("Test Verifier").email("v@test.com").build();

        request = LegalVerificationRequest.builder()
                .id(requestId)
                .owner(User.builder().id(ownerId).fullName("Test Owner").build())
                .executor(User.builder().id(executorId).fullName("Test Executor").build())
                .deathCertificateFileUrlEncrypted("enc://test.pdf")
                .status(LegalVerificationStatus.PENDING)
                .build();
    }

    // Guard cho phép verifierId truy cập hồ sơ
    private void guardAllows(UUID forVerifierId) {
        when(verifierAccessGuard.requireReadable(requestId, forVerifierId)).thenReturn(request);
    }

    private SendVerificationMessageRequest body(String content) {
        return new SendVerificationMessageRequest(content);
    }

    // ---------- history ----------

    @Test
    void history_verifierIsOwner_throwsNotFound() {
        when(verifierAccessGuard.requireReadable(requestId, ownerId))
                .thenThrow(new VerificationRequestNotFoundException("not found"));

        assertThatThrownBy(() -> service.history(requestId, ownerId))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void history_pending_returnsMappedMessages() {
        VerificationExchangeMessage m = VerificationExchangeMessage.builder()
                .id(UUID.randomUUID())
                .request(request)
                .sender(verifier)
                .senderRole(ExchangeSenderRole.VERIFIER)
                .content("Xin chào")
                .attachmentName("a.pdf")
                .attachmentSize(10L)
                .createdAt(LocalDateTime.of(2026, 10, 16, 21, 0))
                .build();
        guardAllows(verifierId);
        when(messageRepository.findHistory(requestId)).thenReturn(List.of(m));

        List<VerificationMessageResponse> result = service.history(requestId, verifierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Xin chào");
        assertThat(result.get(0).getSenderRole()).isEqualTo(ExchangeSenderRole.VERIFIER);
        assertThat(result.get(0).getSenderName()).isEqualTo("Test Verifier");
        assertThat(result.get(0).getAttachmentName()).isEqualTo("a.pdf");
    }

    @Test
    void history_approvedByAnotherVerifier_throwsNotAssigned() {
        when(verifierAccessGuard.requireReadable(requestId, verifierId))
                .thenThrow(new NotAssignedVerifierException("not assigned"));

        assertThatThrownBy(() -> service.history(requestId, verifierId))
                .isInstanceOf(NotAssignedVerifierException.class);

        verifyNoInteractions(messageRepository);
    }

    // ---------- send ----------

    @Test
    void send_textOnly_savesVerifierMessageAuditsAndNotifiesExecutor() {
        guardAllows(verifierId);
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifier));
        when(messageRepository.saveAndFlush(any(VerificationExchangeMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        VerificationMessageResponse result = service.send(requestId, verifierId, null, body("  Bổ sung giấy tờ  "));

        ArgumentCaptor<VerificationExchangeMessage> captor =
                ArgumentCaptor.forClass(VerificationExchangeMessage.class);
        verify(messageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSenderRole()).isEqualTo(ExchangeSenderRole.VERIFIER);
        assertThat(captor.getValue().getContent()).isEqualTo("Bổ sung giấy tờ"); // đã trim
        assertThat(captor.getValue().getAttachmentPath()).isNull();
        assertThat(result.getSenderName()).isEqualTo("Test Verifier");

        verify(auditLogService).log(eq(AuditAction.VERIFICATION_MESSAGE_SENT), eq(AuditResult.SUCCESS),
                eq(verifierId), eq("v@test.com"), eq("LegalVerificationRequest"),
                eq(requestId.toString()), anyString());
        verify(eventPublisher).publishEvent(eq(new VerifierReplyPostedEvent(requestId, executorId)));
    }

    @Test
    void send_emptyContentAndNoFile_throwsInvalid() {
        assertThatThrownBy(() -> service.send(requestId, verifierId, null, body("   ")))
                .isInstanceOf(InvalidVerificationDocumentException.class);

        verifyNoInteractions(messageRepository, eventPublisher);
    }

    @Test
    void send_requestRejected_throwsClosedAndDoesNotSave() {
        request.setStatus(LegalVerificationStatus.REJECTED);
        request.setVerifier(verifier);
        guardAllows(verifierId);

        assertThatThrownBy(() -> service.send(requestId, verifierId, null, body("hi")))
                .isInstanceOf(VerificationRequestClosedException.class);

        verify(messageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void send_verifierIsExecutor_throwsNotFoundAndDoesNotSave() {
        when(verifierAccessGuard.requireReadable(requestId, executorId))
                .thenThrow(new VerificationRequestNotFoundException("not found"));

        assertThatThrownBy(() -> service.send(requestId, executorId, null, body("hi")))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(messageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void send_approvedByAnotherVerifier_throwsNotAssigned() {
        when(verifierAccessGuard.requireReadable(requestId, verifierId))
                .thenThrow(new NotAssignedVerifierException("not assigned"));

        assertThatThrownBy(() -> service.send(requestId, verifierId, null, body("hi")))
                .isInstanceOf(NotAssignedVerifierException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void send_withFile_encryptsStoresAndSavesAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\fakepath\\giay-to.pdf", "application/pdf", "%PDF-1.4 abc".getBytes());
        byte[] plain = "%PDF-1.4 abc".getBytes();
        byte[] encrypted = "cipher".getBytes();

        when(uploadedDocumentValidator.readAndValidate(file)).thenReturn(plain);
        when(cryptoService.encryptBytes(plain)).thenReturn(encrypted);
        guardAllows(verifierId);
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifier));
        when(fileStorageService.storeFile(file, encrypted)).thenReturn("enc://stored.pdf");
        when(messageRepository.saveAndFlush(any(VerificationExchangeMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        VerificationMessageResponse result = service.send(requestId, verifierId, file, null);

        ArgumentCaptor<VerificationExchangeMessage> captor =
                ArgumentCaptor.forClass(VerificationExchangeMessage.class);
        verify(messageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAttachmentPath()).isEqualTo("enc://stored.pdf");
        assertThat(captor.getValue().getAttachmentName()).isEqualTo("giay-to.pdf"); // đã bỏ đường dẫn
        assertThat(captor.getValue().getAttachmentSize()).isEqualTo(plain.length);
        assertThat(result.getAttachmentName()).isEqualTo("giay-to.pdf");
        verify(eventPublisher).publishEvent(eq(new VerifierReplyPostedEvent(requestId, executorId)));
    }

    // n2: người không có quyền bị chặn trước khi server đọc/mã hóa file
    @Test
    void send_withFileButNoAccess_rejectsBeforeReadingOrEncryptingFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "giay-to.pdf", "application/pdf", "%PDF-1.4 abc".getBytes());
        when(verifierAccessGuard.requireReadable(requestId, executorId))
                .thenThrow(new VerificationRequestNotFoundException("not found"));

        assertThatThrownBy(() -> service.send(requestId, executorId, file, null))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verifyNoInteractions(uploadedDocumentValidator, cryptoService, fileStorageService);
        verify(messageRepository, never()).saveAndFlush(any());
    }

    // m2: lỗi runtime của storeFile giữ nguyên kiểu, lỗi checked mới bọc thành IllegalStateException
    @Test
    void send_storeFileThrowsRuntime_keepsOriginalExceptionType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "giay-to.pdf", "application/pdf", "%PDF-1.4 abc".getBytes());
        byte[] plain = "%PDF-1.4 abc".getBytes();
        byte[] encrypted = "cipher".getBytes();

        when(uploadedDocumentValidator.readAndValidate(file)).thenReturn(plain);
        when(cryptoService.encryptBytes(plain)).thenReturn(encrypted);
        guardAllows(verifierId);
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifier));
        when(fileStorageService.storeFile(file, encrypted))
                .thenThrow(new InvalidVerificationDocumentException("boom"));

        assertThatThrownBy(() -> service.send(requestId, verifierId, file, null))
                .isInstanceOf(InvalidVerificationDocumentException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void send_storeFileThrowsChecked_wrapsInIllegalState() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "giay-to.pdf", "application/pdf", "%PDF-1.4 abc".getBytes());
        byte[] plain = "%PDF-1.4 abc".getBytes();
        byte[] encrypted = "cipher".getBytes();

        when(uploadedDocumentValidator.readAndValidate(file)).thenReturn(plain);
        when(cryptoService.encryptBytes(plain)).thenReturn(encrypted);
        guardAllows(verifierId);
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifier));
        when(fileStorageService.storeFile(file, encrypted)).thenThrow(new java.io.IOException("disk full"));

        assertThatThrownBy(() -> service.send(requestId, verifierId, file, null))
                .isInstanceOf(IllegalStateException.class);

        verify(messageRepository, never()).saveAndFlush(any());
    }

    // ---------- getAttachment ----------

    @Test
    void getAttachment_returnsDecryptedBytesAndContentType() throws Exception {
        UUID messageId = UUID.randomUUID();
        byte[] cipher = "cipher".getBytes();
        byte[] plain = "%PDF-1.4 noi dung".getBytes();
        VerificationExchangeMessage m = VerificationExchangeMessage.builder()
                .id(messageId).request(request).sender(verifier)
                .senderRole(ExchangeSenderRole.EXECUTOR)
                .attachmentPath("enc://att.pdf").attachmentName("bosung.pdf").build();

        guardAllows(verifierId);
        when(messageRepository.findByIdAndRequestId(messageId, requestId)).thenReturn(Optional.of(m));
        when(fileStorageService.readFile("enc://att.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);
        when(userRepository.findById(verifierId)).thenReturn(Optional.of(verifier));

        VerificationDocumentDto dto = service.getAttachment(requestId, messageId, verifierId);

        assertThat(dto.getContent()).isEqualTo(plain);
        assertThat(dto.getContentType()).isEqualTo("application/pdf");
        assertThat(dto.getFileName()).isEqualTo("bosung.pdf");
        verify(auditLogService).log(eq(AuditAction.VERIFICATION_ATTACHMENT_VIEWED), eq(AuditResult.SUCCESS),
                eq(verifierId), eq("v@test.com"), eq("VerificationExchangeMessage"),
                eq(messageId.toString()), isNull());
    }

    @Test
    void getAttachment_messageWithoutAttachment_throwsNotFound() throws Exception {
        UUID messageId = UUID.randomUUID();
        VerificationExchangeMessage m = VerificationExchangeMessage.builder()
                .id(messageId).request(request).sender(verifier)
                .senderRole(ExchangeSenderRole.VERIFIER).content("chỉ có chữ").build();

        guardAllows(verifierId);
        when(messageRepository.findByIdAndRequestId(messageId, requestId)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.getAttachment(requestId, messageId, verifierId))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(fileStorageService, never()).readFile(any());
    }

    @Test
    void getAttachment_messageOfAnotherRequest_throwsNotFound() throws Exception {
        UUID messageId = UUID.randomUUID();
        guardAllows(verifierId);
        when(messageRepository.findByIdAndRequestId(messageId, requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAttachment(requestId, messageId, verifierId))
                .isInstanceOf(VerificationRequestNotFoundException.class);

        verify(fileStorageService, never()).readFile(any());
    }
}
