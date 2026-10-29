package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.event.VerificationMessagePostedEvent;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationExchangeServiceImplTest {

    private static final String LOGIN = "executor@example.com";
    private static final byte[] PDF = "%PDF-1.4 extra document".getBytes(StandardCharsets.US_ASCII);

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Mock private VerificationExchangeMessageRepository verificationExchangeMessageRepository;
    @Spy private UploadedDocumentValidator uploadedDocumentValidator = new UploadedDocumentValidator();
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private VerificationExchangeServiceImpl service;

    private UUID assignmentId;
    private UUID requestId;
    private User owner;
    private User executor;
    private User verifier;
    private LegalVerificationRequest verificationRequest;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        owner = User.builder().id(UUID.randomUUID()).fullName("Owner").build();
        executor = User.builder().id(UUID.randomUUID()).email(LOGIN).fullName("Executor Name").build();
        verifier = User.builder().id(UUID.randomUUID()).fullName("Verifier").build();
        verificationRequest = request(LegalVerificationStatus.PENDING, null);
    }

    private LegalVerificationRequest request(LegalVerificationStatus status, User assignedVerifier) {
        return LegalVerificationRequest.builder()
                .id(requestId).owner(owner).executor(executor).verifier(assignedVerifier).status(status).build();
    }

    private void stubGuardAllows() {
        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build();
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(assignment);
    }

    private void stubRequestFound() {
        when(legalVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(verificationRequest));
    }

    private void stubSave() {
        when(verificationExchangeMessageRepository.saveAndFlush(any(VerificationExchangeMessage.class)))
                .thenAnswer(inv -> {
                    VerificationExchangeMessage m = inv.getArgument(0);
                    m.setId(UUID.randomUUID());
                    m.setCreatedAt(LocalDateTime.of(2026, 10, 5, 8, 30));
                    return m;
                });
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private VerificationMessageResponse send(MockMultipartFile file, String content) {
        return service.send(assignmentId, requestId, LOGIN, file, new SendVerificationMessageRequest(content));
    }

    @Test
    @DisplayName("Text only: saved as an EXECUTOR message, trimmed, no file is stored, audit and event are emitted")
    void textOnly_isSavedAndAnnounced() throws Exception {
        stubGuardAllows();
        stubRequestFound();
        stubSave();

        VerificationMessageResponse res = send(null, "  Em xin bổ sung thông tin  ");

        assertEquals("Em xin bổ sung thông tin", res.getContent());
        assertEquals(ExchangeSenderRole.EXECUTOR, res.getSenderRole());
        assertEquals("Executor Name", res.getSenderName());
        assertEquals(requestId, res.getRequestId());
        assertNull(res.getAttachmentName());
        verify(fileStorageService, never()).storeFile(any(), any());
        verify(cryptoService, never()).encryptBytes(any());
        verify(auditLogService).log(eq(AuditAction.VERIFICATION_MESSAGE_SENT), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(LOGIN), eq("LegalVerificationRequest"), eq(requestId.toString()),
                eq("assignmentId=" + assignmentId + ", hasAttachment=false"));
        verify(eventPublisher).publishEvent(new VerificationMessagePostedEvent(
                requestId, owner.getId(), executor.getId(), null));
    }

    @Test
    @DisplayName("File only: content is encrypted, stored once, and only the display name is exposed")
    void fileOnly_isEncryptedAndStored() throws Exception {
        stubGuardAllows();
        stubRequestFound();
        stubSave();
        byte[] cipher = "cipher".getBytes(StandardCharsets.UTF_8);
        when(cryptoService.encryptBytes(PDF)).thenReturn(cipher);
        MockMultipartFile file = pdf("extra.pdf");
        when(fileStorageService.storeFile(file, cipher)).thenReturn("/files/stored-1.pdf");

        VerificationMessageResponse res = send(file, null);

        assertEquals("extra.pdf", res.getAttachmentName());
        assertEquals((long) PDF.length, res.getAttachmentSize());
        assertNull(res.getContent());
        ArgumentCaptor<VerificationExchangeMessage> saved = ArgumentCaptor.forClass(VerificationExchangeMessage.class);
        verify(verificationExchangeMessageRepository).saveAndFlush(saved.capture());
        assertEquals("/files/stored-1.pdf", saved.getValue().getAttachmentPath());
        verify(auditLogService).log(any(), any(), any(), anyString(), anyString(), anyString(),
                eq("assignmentId=" + assignmentId + ", hasAttachment=true"));
    }

    @Test
    @DisplayName("Text and file together are both kept")
    void textAndFile_areBothKept() throws Exception {
        stubGuardAllows();
        stubRequestFound();
        stubSave();
        when(cryptoService.encryptBytes(PDF)).thenReturn(new byte[]{1});
        when(fileStorageService.storeFile(any(), any())).thenReturn("/files/stored-2.pdf");

        VerificationMessageResponse res = send(pdf("doc.pdf"), "Đính kèm giấy xác nhận");

        assertEquals("Đính kèm giấy xác nhận", res.getContent());
        assertEquals("doc.pdf", res.getAttachmentName());
    }

    @Test
    @DisplayName("Path parts in the uploaded file name are dropped before it is stored as a display name")
    void attachmentName_isSanitized() throws Exception {
        stubGuardAllows();
        stubRequestFound();
        stubSave();
        when(cryptoService.encryptBytes(PDF)).thenReturn(new byte[]{1});
        when(fileStorageService.storeFile(any(), any())).thenReturn("/files/stored-3.pdf");

        VerificationMessageResponse res = send(pdf("..\\..\\folder/evil.pdf"), null);

        assertEquals("evil.pdf", res.getAttachmentName());
    }

    @Test
    @DisplayName("Blank text and no (or empty) file -> 400, and nothing else is touched")
    void nothingToSend_isRejected() {
        assertThrows(InvalidVerificationDocumentException.class, () -> send(null, "   "));
        assertThrows(InvalidVerificationDocumentException.class, () -> send(null, null));
        assertThrows(InvalidVerificationDocumentException.class,
                () -> send(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]), ""));

        verifyNoInteractions(executorAccessGuard, legalVerificationRequestRepository, fileStorageService);
    }

    @Test
    @DisplayName("Attachment that is not PDF/PNG/JPG is rejected before any lookup or storage")
    void invalidAttachment_isRejectedEarly() throws Exception {
        MockMultipartFile script = new MockMultipartFile("file", "a.pdf", "application/pdf",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidVerificationDocumentException.class, () -> send(script, "text"));

        verifyNoInteractions(executorAccessGuard, legalVerificationRequestRepository);
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("Guard rejects with 404 or 403 -> request is never loaded and nothing is stored")
    void guardRejection_stopsEverything() throws Exception {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."))
                .thenThrow(new ExecutorNotActiveException("Access is not available yet"));

        assertThrows(ExecutorAssignmentNotFoundException.class, () -> send(null, "text"));
        assertThrows(ExecutorNotActiveException.class, () -> send(null, "text"));

        verifyNoInteractions(legalVerificationRequestRepository);
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("Unknown request, another executor's request, or another owner's request -> 404 and no file stored")
    void foreignRequest_isNotFound() throws Exception {
        stubGuardAllows();
        User otherExecutor = User.builder().id(UUID.randomUUID()).build();
        User otherOwner = User.builder().id(UUID.randomUUID()).build();
        when(legalVerificationRequestRepository.findById(requestId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(LegalVerificationRequest.builder().id(requestId).owner(owner)
                        .executor(otherExecutor).status(LegalVerificationStatus.PENDING).build()))
                .thenReturn(Optional.of(LegalVerificationRequest.builder().id(requestId).owner(otherOwner)
                        .executor(executor).status(LegalVerificationStatus.PENDING).build()));
        when(cryptoService.encryptBytes(PDF)).thenReturn(new byte[]{1});

        for (int i = 0; i < 3; i++) {
            assertThrows(VerificationRequestNotFoundException.class, () -> send(pdf("a.pdf"), "text"));
        }

        verify(fileStorageService, never()).storeFile(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("REJECTED and SIGNED requests are closed -> 409, nothing stored, no event")
    void closedRequest_isRejectedWithConflict() throws Exception {
        stubGuardAllows();
        when(cryptoService.encryptBytes(PDF)).thenReturn(new byte[]{1});
        when(legalVerificationRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request(LegalVerificationStatus.REJECTED, null)))
                .thenReturn(Optional.of(request(LegalVerificationStatus.SIGNED, null)));

        assertThrows(VerificationRequestClosedException.class, () -> send(pdf("a.pdf"), "text"));
        assertThrows(VerificationRequestClosedException.class, () -> send(pdf("a.pdf"), "text"));

        verify(fileStorageService, never()).storeFile(any(), any());
        verify(verificationExchangeMessageRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("An APPROVED request (not signed yet) still accepts messages")
    void approvedRequest_stillAcceptsMessages() {
        stubGuardAllows();
        when(legalVerificationRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request(LegalVerificationStatus.APPROVED, null)));
        stubSave();

        VerificationMessageResponse res = send(null, "Bổ sung");

        assertEquals("Bổ sung", res.getContent());
    }

    @Test
    @DisplayName("When a verifier is assigned the event carries the verifier id")
    void assignedVerifier_isCarriedInEvent() {
        stubGuardAllows();
        when(legalVerificationRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request(LegalVerificationStatus.PENDING, verifier)));
        stubSave();

        send(null, "Xin chào");

        verify(eventPublisher).publishEvent(new VerificationMessagePostedEvent(
                requestId, owner.getId(), executor.getId(), verifier.getId()));
    }

    @Test
    @DisplayName("Database failure after the file was stored -> file is deleted once, no event")
    void saveFailure_deletesStoredFile() throws Exception {
        stubGuardAllows();
        stubRequestFound();
        when(cryptoService.encryptBytes(PDF)).thenReturn(new byte[]{1});
        when(fileStorageService.storeFile(any(), any())).thenReturn("/files/orphan.pdf");
        when(verificationExchangeMessageRepository.saveAndFlush(any()))
                .thenThrow(new IllegalStateException("database error"));

        assertThrows(IllegalStateException.class, () -> send(pdf("a.pdf"), "text"));

        verify(fileStorageService, times(1)).deleteFile("/files/orphan.pdf");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Response never exposes the stored path or any e-mail address")
    void response_hasNoSensitiveFields() {
        for (Field field : VerificationMessageResponse.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("path") || name.contains("email") || name.contains("url"),
                    "Sensitive field in DTO: " + field.getName());
        }
    }

    @Test
    @DisplayName("FR-11 history: newest first page, both roles, readable when closed, marks executor read")
    void history_returnsMessagesOfOwnRequest() {
        stubGuardAllows();
        when(legalVerificationRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request(LegalVerificationStatus.REJECTED, verifier)));
        VerificationExchangeMessage first = VerificationExchangeMessage.builder().id(UUID.randomUUID())
                .request(verificationRequest).sender(executor).senderRole(ExchangeSenderRole.EXECUTOR)
                .content("Câu hỏi").createdAt(LocalDateTime.of(2026, 10, 5, 8, 0)).build();
        VerificationExchangeMessage second = VerificationExchangeMessage.builder().id(UUID.randomUUID())
                .request(verificationRequest).sender(verifier).senderRole(ExchangeSenderRole.VERIFIER)
                .content("Trả lời").attachmentName("form.pdf").attachmentSize(10L)
                .createdAt(LocalDateTime.of(2026, 10, 5, 9, 0)).build();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(verificationExchangeMessageRepository.findHistoryPage(eq(requestId), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(second, first), inv.getArgument(1), 2));

        List<VerificationMessageResponse> history = service.history(assignmentId, requestId, LOGIN, 0, 500)
                .getContent();

        assertEquals(2, history.size());
        assertEquals(ExchangeSenderRole.VERIFIER, history.get(0).getSenderRole());
        assertEquals("form.pdf", history.get(0).getAttachmentName());
        assertEquals(ExchangeSenderRole.EXECUTOR, history.get(1).getSenderRole());
        assertEquals("Executor Name", history.get(1).getSenderName());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
        // Mốc đọc = createdAt của tin mới nhất đã trả về, không phải now()
        verify(legalVerificationRequestRepository).markExecutorRead(requestId, second.getCreatedAt());
    }

    @Test
    @DisplayName("FR-11 history: older page (page > 0) or empty page does not move the read marker")
    void history_olderOrEmptyPage_doesNotMarkRead() {
        stubGuardAllows();
        when(legalVerificationRequestRepository.findById(requestId))
                .thenReturn(Optional.of(request(LegalVerificationStatus.PENDING, verifier)));
        VerificationExchangeMessage old = VerificationExchangeMessage.builder().id(UUID.randomUUID())
                .request(verificationRequest).sender(verifier).senderRole(ExchangeSenderRole.VERIFIER)
                .content("Cũ").createdAt(LocalDateTime.of(2026, 10, 1, 8, 0)).build();
        when(verificationExchangeMessageRepository.findHistoryPage(eq(requestId), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(old), inv.getArgument(1), 51))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        service.history(assignmentId, requestId, LOGIN, 1, 50);
        service.history(assignmentId, requestId, LOGIN, 0, 50);

        verify(legalVerificationRequestRepository, never()).markExecutorRead(any(), any());
    }

    @Test
    @DisplayName("History of someone else's request -> 404 and no message is read")
    void history_ofForeignRequest_isNotFound() {
        stubGuardAllows();
        when(legalVerificationRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThrows(VerificationRequestNotFoundException.class, () -> service.history(assignmentId, requestId, LOGIN, 0, 50));

        verifyNoInteractions(verificationExchangeMessageRepository);
        verify(legalVerificationRequestRepository, never()).markExecutorRead(any(), any());
    }

    @Test
    @DisplayName("History with a guard rejection reads nothing")
    void history_guardRejection_readsNothing() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        assertThrows(ExecutorAssignmentNotFoundException.class, () -> service.history(assignmentId, requestId, LOGIN, 0, 50));

        verifyNoInteractions(legalVerificationRequestRepository, verificationExchangeMessageRepository);
    }

    // ---------- B5: tải tệp đính kèm ----------

    private VerificationExchangeMessage messageWithAttachment(UUID messageId, String path, String name) {
        VerificationExchangeMessage m = VerificationExchangeMessage.builder()
                .id(messageId).request(verificationRequest).sender(verifier)
                .senderRole(ExchangeSenderRole.VERIFIER).attachmentPath(path).attachmentName(name).build();
        when(verificationExchangeMessageRepository.findByIdAndRequestId(messageId, requestId))
                .thenReturn(Optional.of(m));
        return m;
    }

    @Test
    @DisplayName("Attachment: decrypted bytes, content type from magic bytes, original name, audit VIEWED")
    void attachment_returnsDecryptedBytesAndAudits() throws Exception {
        UUID messageId = UUID.randomUUID();
        stubGuardAllows();
        stubRequestFound();
        messageWithAttachment(messageId, "stored/a.enc", "giay-to.pdf");
        byte[] cipher = "cipher".getBytes(StandardCharsets.US_ASCII);
        when(fileStorageService.readFile("stored/a.enc")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(PDF);

        VerificationDocumentDto doc = service.getAttachment(assignmentId, requestId, messageId, LOGIN);

        assertArrayEquals(PDF, doc.getContent());
        assertEquals("application/pdf", doc.getContentType());
        assertEquals("giay-to.pdf", doc.getFileName());
        verify(auditLogService).log(eq(AuditAction.VERIFICATION_ATTACHMENT_VIEWED), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(LOGIN), eq("VerificationExchangeMessage"), eq(messageId.toString()),
                anyString());
    }

    @Test
    @DisplayName("Attachment without a stored name falls back to 'attachment'")
    void attachment_withoutName_usesFallbackName() throws Exception {
        UUID messageId = UUID.randomUUID();
        stubGuardAllows();
        stubRequestFound();
        messageWithAttachment(messageId, "stored/a.enc", null);
        when(fileStorageService.readFile("stored/a.enc")).thenReturn(new byte[]{1});
        when(cryptoService.decryptBytes(any())).thenReturn(PDF);

        assertEquals("attachment", service.getAttachment(assignmentId, requestId, messageId, LOGIN).getFileName());
    }

    @Test
    @DisplayName("Text-only message or message of another request -> 404, no file is read")
    void attachment_missingOrTextOnly_isNotFound() throws Exception {
        UUID textOnly = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        stubGuardAllows();
        stubRequestFound();
        messageWithAttachment(textOnly, null, null);
        when(verificationExchangeMessageRepository.findByIdAndRequestId(unknown, requestId))
                .thenReturn(Optional.empty());

        assertThrows(VerificationRequestNotFoundException.class,
                () -> service.getAttachment(assignmentId, requestId, textOnly, LOGIN));
        assertThrows(VerificationRequestNotFoundException.class,
                () -> service.getAttachment(assignmentId, requestId, unknown, LOGIN));

        verifyNoInteractions(fileStorageService, cryptoService, auditLogService);
    }

    @Test
    @DisplayName("Attachment of a request filed by another executor -> 404, message is never looked up")
    void attachment_ofForeignRequest_isNotFound() {
        stubGuardAllows();
        User otherExecutor = User.builder().id(UUID.randomUUID()).build();
        when(legalVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(
                LegalVerificationRequest.builder().id(requestId).owner(owner).executor(otherExecutor)
                        .status(LegalVerificationStatus.PENDING).build()));

        assertThrows(VerificationRequestNotFoundException.class,
                () -> service.getAttachment(assignmentId, requestId, UUID.randomUUID(), LOGIN));

        verifyNoInteractions(verificationExchangeMessageRepository, fileStorageService);
    }

    @Test
    @DisplayName("Attachment with a guard rejection (wrong user / not active) reads nothing")
    void attachment_guardRejection_readsNothing() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorNotActiveException("Access is not available yet"));

        assertThrows(ExecutorNotActiveException.class,
                () -> service.getAttachment(assignmentId, requestId, UUID.randomUUID(), LOGIN));

        verifyNoInteractions(legalVerificationRequestRepository, verificationExchangeMessageRepository,
                fileStorageService);
    }

    @Test
    @DisplayName("Decrypt failure -> generic IllegalStateException, no audit")
    void attachment_decryptFailure_isGenericError() throws Exception {
        UUID messageId = UUID.randomUUID();
        stubGuardAllows();
        stubRequestFound();
        messageWithAttachment(messageId, "stored/a.enc", "a.pdf");
        when(fileStorageService.readFile("stored/a.enc")).thenReturn(new byte[]{1});
        when(cryptoService.decryptBytes(any())).thenThrow(new javax.crypto.AEADBadTagException("tag"));

        assertThrows(IllegalStateException.class,
                () -> service.getAttachment(assignmentId, requestId, messageId, LOGIN));
        verifyNoInteractions(auditLogService);
    }
}
