package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.ExecutorEvidenceFile;
import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TaskConfirmationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.ExecutorEvidenceFileRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.utility.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorEvidenceServiceImplTest {

    private static final String LOGIN = "executor@example.com";

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private ExecutorTaskRepository executorTaskRepository;
    @Mock private ExecutorEvidenceFileRepository evidenceRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;
    @Spy
    private UploadedDocumentValidator uploadedDocumentValidator = new UploadedDocumentValidator();
    @InjectMocks private ExecutorEvidenceServiceImpl service;

    private final UUID assignmentId = UUID.randomUUID();
    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
    private final User executor = User.builder().id(UUID.randomUUID()).email(LOGIN).build();
    private Vault vault;
    private ExecutorTask task;

    @BeforeEach
    void setUp() {
        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault A").status(VaultStatus.UNLOCKED).build();
        DigitalAsset asset = DigitalAsset.builder().id(UUID.randomUUID()).vault(vault).assetType(AssetType.SOCIAL_MEDIA)
                .status(AssetStatus.ACTIVE).build();
        task = ExecutorTask.builder().id(UUID.randomUUID()).vault(vault).asset(asset).platform("Facebook")
                .wish(ExecutorTaskWish.DELETE).status(ExecutorTaskStatus.DOING).build();
    }

    private void stubAccess() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build());
        when(executorTaskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));
    }

    private static MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "xacnhan.pdf", "application/pdf", bytes);
    }

    @Test
    void upload_storesEncryptedFile_setsHashOnTask_andAuditsHashOnly() throws Exception {
        stubAccess();
        byte[] bytes = "%PDF-1.4 noi dung bang chung".getBytes(StandardCharsets.UTF_8);
        when(evidenceRepository.findByTaskId(task.getId())).thenReturn(Optional.empty());
        when(cryptoService.encryptBytes(bytes)).thenReturn(new byte[]{1, 2, 3});
        when(fileStorageService.storeFile(any(), any())).thenReturn("/uploads/new.enc");
        when(evidenceRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ExecutorEvidenceFile e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(executorTaskRepository.saveAndFlush(task)).thenReturn(task);

        ExecutorTaskResponse r = service.upload(assignmentId, task.getId(), pdf(bytes), LOGIN);

        String hash = Sha256.hex(bytes);
        assertThat(r.isHasEvidenceDocument()).isTrue();
        assertEquals(hash, r.getEvidenceSha256());
        assertEquals("xacnhan.pdf", r.getEvidenceFileName());
        assertThat(task.getEvidenceUploadedAt()).isNotNull();
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_EVIDENCE_ADDED), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(LOGIN), eq(vault.getId()), eq("ExecutorTask"), eq(task.getId().toString()),
                contains(hash));
    }

    @Test
    void upload_replacesExistingFile_andDeletesOldOne() throws Exception {
        stubAccess();
        ExecutorEvidenceFile existing = ExecutorEvidenceFile.builder().id(UUID.randomUUID()).task(task).vault(vault)
                .fileName("cu.pdf").fileUrlEncrypted("/uploads/old.enc").sha256("x").build();
        when(evidenceRepository.findByTaskId(task.getId())).thenReturn(Optional.of(existing));
        when(cryptoService.encryptBytes(any())).thenReturn(new byte[]{9});
        when(fileStorageService.storeFile(any(), any())).thenReturn("/uploads/new.enc");
        when(evidenceRepository.saveAndFlush(existing)).thenReturn(existing);
        when(executorTaskRepository.saveAndFlush(task)).thenReturn(task);

        service.upload(assignmentId, task.getId(), pdf("%PDF-1.4 moi".getBytes(StandardCharsets.UTF_8)), LOGIN);

        assertEquals("/uploads/new.enc", existing.getFileUrlEncrypted());
        verify(fileStorageService).deleteFile("/uploads/old.enc");
    }

    @Test
    void upload_afterBeneficiaryConfirmed_conflict() throws Exception {
        stubAccess();
        task.setConfirmationStatus(TaskConfirmationStatus.CONFIRMED);
        task.setStatus(ExecutorTaskStatus.DONE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(assignmentId, task.getId(), pdf(new byte[]{1}), LOGIN));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    void upload_emptyFile_orWrongType_rejected() throws Exception {
        stubAccess();

        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.upload(assignmentId, task.getId(), pdf(new byte[0]), LOGIN));
        BusinessException exe = assertThrows(BusinessException.class, () -> service.upload(assignmentId, task.getId(),
                new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{1}), LOGIN));

        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, empty.getErrorCode());
        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, exe.getErrorCode());
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    private void stubDownloadAccess() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build());
        when(executorTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
    }

    @Test
    void download_noEvidenceFile_notFound() {
        stubDownloadAccess();
        when(evidenceRepository.findByTaskId(task.getId())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.download(assignmentId, task.getId(), LOGIN));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void download_returnsDecryptedFile_andAudits() throws Exception {
        stubDownloadAccess();
        ExecutorEvidenceFile evidence = ExecutorEvidenceFile.builder().id(UUID.randomUUID()).task(task).vault(vault)
                .fileName("xacnhan.pdf").fileUrlEncrypted("/uploads/e.enc").sha256("x").build();
        byte[] cipher = new byte[]{9};
        byte[] plain = "%PDF-1.4 x".getBytes(StandardCharsets.UTF_8);
        when(evidenceRepository.findByTaskId(task.getId())).thenReturn(Optional.of(evidence));
        when(fileStorageService.readFile("/uploads/e.enc")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        DocumentDownloadResponse r = service.download(assignmentId, task.getId(), LOGIN);

        assertEquals("xacnhan.pdf", r.getFileName());
        assertEquals("application/pdf", r.getContentType());
        assertThat(r.getContent()).isEqualTo(plain);
        verify(auditLogService).log(eq(AuditAction.DOCUMENT_DOWNLOADED), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(LOGIN), eq(vault.getId()), eq("ExecutorEvidence"),
                eq(evidence.getId().toString()), any());
    }
}
