package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SubmitLegalVerificationRequest;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationMessage;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidDeathCertificateException;
import com.ltld.app.legacyvault.exception.InvalidVerificationRequestException;
import com.ltld.app.legacyvault.exception.VaultStateConflictException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.VerificationMessageRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
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
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalVerificationSubmissionServiceImplTest {

    private static final byte[] PDF_CONTENT = "%PDF-1.7 giay chung tu".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENCRYPTED = new byte[]{1, 2, 3};
    private static final String STORED_PATH = "/srv/uploads/0b6a.pdf";

    @Mock private VaultRepository vaultRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private LegalVerificationRequestRepository requestRepository;
    @Mock private VerificationRequestVaultRepository requestVaultRepository;
    @Mock private VerificationMessageRepository messageRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CryptoService cryptoService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private LegalVerificationSubmissionServiceImpl submissionService;

    private User owner;
    private User executor;
    private Vault vault1;
    private Vault vault2;
    private ExecutorAssignment activeAssignment;
    private UUID requestId;

    @BeforeEach
    void setUp() throws Exception {
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").fullName("Owner Test").build();
        executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").fullName("Executor Test").build();

        vault1 = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault 1").status(VaultStatus.ACTIVE).build();
        vault2 = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault 2").status(VaultStatus.ACTIVE).build();

        activeAssignment = ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .executorUser(executor)
                .executorType(ExecutorType.PRIMARY)
                .status(ExecutorStatus.ACTIVE)
                .activatedAt(LocalDateTime.now().minusDays(1))
                .build();

        requestId = UUID.randomUUID();

        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        // Trả về đúng các Vault được yêu cầu, như truy vấn IN thật
        when(vaultRepository.findAllByIdInForUpdate(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return java.util.stream.Stream.of(vault1, vault2).filter(v -> ids.contains(v.getId())).toList();
        });
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(List.of(activeAssignment));
        when(requestVaultRepository.existsByVaultIdInAndRequestStatusIn(anyCollection(), anyCollection()))
                .thenReturn(false);
        when(cryptoService.encryptBytes(PDF_CONTENT)).thenReturn(ENCRYPTED);
        when(fileStorageService.storeFile(any(), eq(ENCRYPTED))).thenReturn(STORED_PATH);
        when(requestRepository.saveAndFlush(any(LegalVerificationRequest.class))).thenAnswer(inv -> {
            LegalVerificationRequest r = inv.getArgument(0);
            r.setId(requestId);
            r.setCreatedAt(LocalDateTime.now());
            return r;
        });
        when(userRepository.findAllByRolesCode("LEGAL_VERIFIER")).thenReturn(List.of());
    }

    private SubmitLegalVerificationRequest request(MockMultipartFile file, String note, UUID... vaultIds) {
        return SubmitLegalVerificationRequest.builder()
                .deathCertificate(file)
                .vaultIds(List.of(vaultIds))
                .note(note)
                .build();
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("deathCertificate", "giay-chung-tu.pdf", "application/pdf", PDF_CONTENT);
    }

    @Test
    @DisplayName("Happy path: tạo yêu cầu PENDING, gắn Vault, lưu ghi chú, ghi audit và trả DTO")
    @SuppressWarnings("unchecked")
    void submit_happyPath_createsPendingRequest() throws Exception {
        VerificationRequestResponseDto result = submissionService.submit(
                request(pdf(), "  Gửi kèm bản scan  ", vault1.getId(), vault2.getId()), executor.getEmail());

        ArgumentCaptor<LegalVerificationRequest> requestCaptor = ArgumentCaptor.forClass(LegalVerificationRequest.class);
        verify(requestRepository).saveAndFlush(requestCaptor.capture());
        LegalVerificationRequest saved = requestCaptor.getValue();
        assertThat(saved.getOwner()).isSameAs(owner);
        assertThat(saved.getExecutor()).isSameAs(executor);
        assertThat(saved.getDeathCertificateFileUrlEncrypted()).isEqualTo(STORED_PATH);
        assertThat(saved.getStatus()).isEqualTo(LegalVerificationStatus.PENDING);
        assertThat(saved.getVerifier()).isNull();

        // Nội dung tệp được mã hóa trước khi ghi xuống đĩa
        verify(cryptoService).encryptBytes(PDF_CONTENT);
        verify(fileStorageService).storeFile(any(), eq(ENCRYPTED));

        ArgumentCaptor<List<VerificationRequestVault>> linksCaptor = ArgumentCaptor.forClass(List.class);
        verify(requestVaultRepository).saveAll(linksCaptor.capture());
        assertThat(linksCaptor.getValue())
                .extracting(VerificationRequestVault::getVault)
                .containsExactly(vault1, vault2);
        assertThat(linksCaptor.getValue()).allMatch(link -> link.getRequest() == saved);

        ArgumentCaptor<VerificationMessage> messageCaptor = ArgumentCaptor.forClass(VerificationMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSender()).isSameAs(executor);
        assertThat(messageCaptor.getValue().getRequest()).isSameAs(saved);
        assertThat(messageCaptor.getValue().getMessageText()).isEqualTo("Gửi kèm bản scan");

        verify(auditLogService).log(eq(AuditAction.LEGAL_REQUEST_SUBMITTED), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(executor.getEmail()),
                eq("LegalVerificationRequest"), eq(requestId.toString()), any());

        assertThat(result.getId()).isEqualTo(requestId);
        assertThat(result.getStatus()).isEqualTo(LegalVerificationStatus.PENDING);
        assertThat(result.getOwnerName()).isEqualTo("Owner Test");
        assertThat(result.getExecutorName()).isEqualTo("Executor Test");
        assertThat(result.isHasDeathCertificate()).isTrue();
        assertThat(result.getVaultIds()).containsExactly(vault1.getId(), vault2.getId());
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Không có ghi chú: không tạo VerificationMessage")
    void submit_blankNote_doesNotCreateMessage() {
        submissionService.submit(request(pdf(), "   ", vault1.getId()), executor.getEmail());

        verify(messageRepository, never()).save(any());
        verify(requestRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("Vault trùng trong danh sách chỉ được khóa và gắn một lần")
    void submit_duplicateVaultIdsInRequest_areDeduplicated() {
        VerificationRequestResponseDto result = submissionService.submit(
                request(pdf(), null, vault1.getId(), vault1.getId()), executor.getEmail());

        verify(vaultRepository).findAllByIdInForUpdate(List.of(vault1.getId()));
        assertThat(result.getVaultIds()).containsExactly(vault1.getId());
    }

    @Test
    @DisplayName("Thông báo IN_APP cho Legal Verifier, bỏ qua người là Executor hoặc Owner của yêu cầu")
    @SuppressWarnings("unchecked")
    void submit_notifiesLegalVerifiersExceptConflictedUsers() {
        User verifier = User.builder().id(UUID.randomUUID()).email("verifier@example.com").build();
        when(userRepository.findAllByRolesCode("LEGAL_VERIFIER")).thenReturn(List.of(verifier, executor, owner));

        submissionService.submit(request(pdf(), null, vault1.getId()), executor.getEmail());

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        Notification notification = captor.getValue().getFirst();
        assertThat(notification.getRecipient()).isSameAs(verifier);
        assertThat(notification.getType()).isEqualTo(NotificationType.VERIFICATION_REQUEST);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(notification.getReferenceType()).isEqualTo("LegalVerificationRequest");
        assertThat(notification.getReferenceId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("Không tìm thấy người dùng đăng nhập: ném ExecutorAssignmentNotFoundException")
    void submit_unknownUser_throwsNotFound() {
        assertThatThrownBy(() -> submissionService.submit(request(pdf(), null, vault1.getId()), "ghost@example.com"))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);

        verifyNoInteractions(fileStorageService);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Vault không tồn tại: trả lỗi giống Vault không được ủy quyền (404)")
    void submit_missingVault_throwsNotFound() {
        assertThatThrownBy(() -> submissionService.submit(
                request(pdf(), null, vault1.getId(), UUID.randomUUID()), executor.getEmail()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class)
                .hasMessage("Executor not assigned to this vault.");

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Vault thuộc nhiều Owner khác nhau: ném InvalidVerificationRequestException")
    void submit_vaultsOfDifferentOwners_throwsInvalidRequest() {
        User otherOwner = User.builder().id(UUID.randomUUID()).build();
        vault2.setOwner(otherOwner);

        assertThatThrownBy(() -> submissionService.submit(
                request(pdf(), null, vault1.getId(), vault2.getId()), executor.getEmail()))
                .isInstanceOf(InvalidVerificationRequestException.class);

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Executor không được Owner của Vault phân công: ném ExecutorAssignmentNotFoundException")
    void submit_notAssignedToOwner_throwsNotFound() {
        ExecutorAssignment otherOwnerAssignment = ExecutorAssignment.builder()
                .owner(User.builder().id(UUID.randomUUID()).build())
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(List.of(otherOwnerAssignment));

        assertThatThrownBy(() -> submissionService.submit(request(pdf(), null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Executor chưa được kích hoạt: ném ExecutorNotActiveException")
    void submit_executorNotActive_throwsNotActive() {
        activeAssignment.setStatus(ExecutorStatus.INACTIVE);

        assertThatThrownBy(() -> submissionService.submit(request(pdf(), null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(ExecutorNotActiveException.class);

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Vault không ở trạng thái ACTIVE: ném VaultStateConflictException")
    void submit_vaultNotActive_throwsConflict() {
        vault2.setStatus(VaultStatus.UNLOCKED);

        assertThatThrownBy(() -> submissionService.submit(
                request(pdf(), null, vault1.getId(), vault2.getId()), executor.getEmail()))
                .isInstanceOf(VaultStateConflictException.class)
                .hasMessageContaining(vault2.getId().toString());

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Đã có yêu cầu đang mở cho Vault (4a): ném DuplicateVerificationRequestException")
    void submit_openRequestExists_throwsDuplicate() {
        when(requestVaultRepository.existsByVaultIdInAndRequestStatusIn(
                List.of(vault1.getId()),
                java.util.Set.of(LegalVerificationStatus.PENDING, LegalVerificationStatus.APPROVED)))
                .thenReturn(true);

        assertThatThrownBy(() -> submissionService.submit(request(pdf(), null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(DuplicateVerificationRequestException.class);

        verifyNoInteractions(fileStorageService);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tệp sai đuôi (2a): ném InvalidDeathCertificateException trước khi truy vấn DB")
    void submit_unsupportedExtension_throwsInvalidFile() {
        MockMultipartFile docx = new MockMultipartFile("deathCertificate", "scan.docx",
                "application/octet-stream", PDF_CONTENT);

        assertThatThrownBy(() -> submissionService.submit(request(docx, null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(InvalidDeathCertificateException.class);

        verifyNoInteractions(vaultRepository, fileStorageService);
    }

    @Test
    @DisplayName("Nội dung tệp không khớp đuôi .pdf (2a): ném InvalidDeathCertificateException")
    void submit_contentDoesNotMatchExtension_throwsInvalidFile() {
        MockMultipartFile fakePdf = new MockMultipartFile("deathCertificate", "giay-chung-tu.pdf",
                "application/pdf", "not really a pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> submissionService.submit(request(fakePdf, null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(InvalidDeathCertificateException.class);

        verifyNoInteractions(vaultRepository, fileStorageService);
    }

    @Test
    @DisplayName("Ảnh .JPG chữ hoa với nội dung JPEG hợp lệ được chấp nhận")
    void submit_uppercaseJpg_isAccepted() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        when(cryptoService.encryptBytes(jpeg)).thenReturn(ENCRYPTED);
        MockMultipartFile jpg = new MockMultipartFile("deathCertificate", "SCAN.JPG", "image/jpeg", jpeg);

        submissionService.submit(request(jpg, null, vault1.getId()), executor.getEmail());

        verify(fileStorageService).storeFile(jpg, ENCRYPTED);
    }

    @Test
    @DisplayName("Tệp rỗng (2a): ném InvalidDeathCertificateException")
    void submit_emptyFile_throwsInvalidFile() {
        MockMultipartFile empty = new MockMultipartFile("deathCertificate", "giay-chung-tu.pdf",
                "application/pdf", new byte[0]);

        assertThatThrownBy(() -> submissionService.submit(request(empty, null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(InvalidDeathCertificateException.class);

        verifyNoInteractions(vaultRepository, fileStorageService);
    }

    @Test
    @DisplayName("Tệp vượt quá 10MB: ném InvalidDeathCertificateException")
    void submit_fileTooLarge_throwsInvalidFile() {
        byte[] large = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF_CONTENT, 0, large, 0, PDF_CONTENT.length);
        MockMultipartFile big = new MockMultipartFile("deathCertificate", "giay-chung-tu.pdf",
                "application/pdf", large);

        assertThatThrownBy(() -> submissionService.submit(request(big, null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(InvalidDeathCertificateException.class)
                .hasMessageContaining("10MB");

        verifyNoInteractions(vaultRepository, fileStorageService);
    }

    @Test
    @DisplayName("Lỗi ghi tệp: không tạo yêu cầu")
    void submit_storageFailure_doesNotSaveRequest() throws Exception {
        when(fileStorageService.storeFile(any(), any())).thenThrow(new java.io.IOException("disk full"));

        assertThatThrownBy(() -> submissionService.submit(request(pdf(), null, vault1.getId()), executor.getEmail()))
                .isInstanceOf(IllegalStateException.class);

        verify(requestRepository, never()).saveAndFlush(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
