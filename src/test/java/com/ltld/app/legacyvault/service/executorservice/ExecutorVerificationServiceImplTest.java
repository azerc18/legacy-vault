package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutorVerificationServiceImplTest {

    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Mock private VerificationRequestVaultRepository verificationRequestVaultRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private ExecutorVerificationServiceImpl service;

    private static final String LOGIN = "executor@mail.com";
    private static final byte[] PDF = "%PDF-1.4 test content".getBytes(StandardCharsets.US_ASCII);

    private User owner;
    private User executor;
    private UUID assignmentId;
    private Vault vault1;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(UUID.randomUUID());
        executor = new User();
        executor.setId(UUID.randomUUID());
        executor.setEmail(LOGIN);
        assignmentId = UUID.randomUUID();

        vault1 = Vault.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .status(VaultStatus.ACTIVE)
                .build();
    }

    private ExecutorAssignment assignment(ExecutorStatus status) {
        return ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .executorType(ExecutorType.PRIMARY)
                .status(status)
                .build();
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "death-cert.pdf", "application/pdf", PDF);
    }

    private void stubDmsTriggered() {
        DmsConfig config = new DmsConfig();
        config.setStatus(DmsStatus.TRIGGERED);
        when(dmsConfigRepository.findByOwnerIdForUpdate(owner.getId())).thenReturn(Optional.of(config));
    }

    @Test
    @DisplayName("Happy path: store encrypted file, link vaults, create PENDING request and audit")
    void submit_happyPath() throws Exception {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        stubDmsTriggered();
        when(legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(eq(owner.getId()), any()))
                .thenReturn(false);
        when(vaultRepository.findById(vault1.getId())).thenReturn(Optional.of(vault1));

        byte[] cipher = "cipher".getBytes(StandardCharsets.UTF_8);
        when(cryptoService.encryptBytes(any())).thenReturn(cipher);
        when(fileStorageService.storeFile(any(), any())).thenReturn("/files/cert.pdf");
        when(cryptoService.encrypt("/files/cert.pdf")).thenReturn("ENC_PATH");
        when(legalVerificationRequestRepository.saveAndFlush(any(LegalVerificationRequest.class)))
                .thenAnswer(inv -> {
                    LegalVerificationRequest r = inv.getArgument(0);
                    r.setId(UUID.randomUUID());
                    return r;
                });

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "  note  ");
        DeathVerificationResponse res = service.submit(assignmentId, LOGIN, pdf(), req);

        assertEquals(LegalVerificationStatus.PENDING, res.getStatus());
        assertEquals("note", res.getNotes());
        assertEquals(1, res.getVaultIds().size());

        ArgumentCaptor<LegalVerificationRequest> captor = ArgumentCaptor.forClass(LegalVerificationRequest.class);
        verify(legalVerificationRequestRepository).saveAndFlush(captor.capture());
        assertEquals("ENC_PATH", captor.getValue().getDeathCertificateFileUrlEncrypted());
        assertEquals(owner, captor.getValue().getOwner());

        verify(verificationRequestVaultRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
        verify(fileStorageService).storeFile(any(), eq(cipher));
        verify(auditLogRepository).save(argThat(a -> a.getAction() == AuditAction.LEGAL_VERIFICATION_REQUESTED
                && a.getActor() == executor));
    }

    @Test
    @DisplayName("Empty vaultIds -> InvalidVerificationDocumentException (400)")
    void submit_emptyVaultIds_badRequest() {
        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(Collections.emptyList(), "note");
        assertThrows(InvalidVerificationDocumentException.class,
                () -> service.submit(assignmentId, LOGIN, pdf(), req));

        verify(executorAssignmentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Vault does not belong to owner -> VaultException (404)")
    void submit_vaultNotOwned_notFound() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        stubDmsTriggered();
        when(legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(eq(owner.getId()), any()))
                .thenReturn(false);

        User someoneElse = new User();
        someoneElse.setId(UUID.randomUUID());
        Vault otherVault = Vault.builder().id(UUID.randomUUID()).owner(someoneElse).status(VaultStatus.ACTIVE).build();
        when(vaultRepository.findById(otherVault.getId())).thenReturn(Optional.of(otherVault));

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(otherVault.getId()), "note");
        VaultException ex = assertThrows(VaultException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("Vault is not ACTIVE -> VaultException (409)")
    void submit_vaultNotActive_conflict() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        stubDmsTriggered();
        when(legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(eq(owner.getId()), any()))
                .thenReturn(false);

        Vault unlockedVault = Vault.builder().id(UUID.randomUUID()).owner(owner).status(VaultStatus.UNLOCKED).build();
        when(vaultRepository.findById(unlockedVault.getId())).thenReturn(Optional.of(unlockedVault));

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(unlockedVault.getId()), "note");
        VaultException ex = assertThrows(VaultException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    @DisplayName("Inactive executor -> ExecutorNotActiveException (403)")
    void submit_inactiveExecutor_forbidden() throws Exception {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.INACTIVE)));

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), null);
        assertThrows(ExecutorNotActiveException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));

        verify(fileStorageService, never()).storeFile(any(), any());
        verify(legalVerificationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("DMS not TRIGGERED -> DmsNotTriggeredException (409)")
    void submit_dmsNotTriggered_conflict() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        DmsConfig config = new DmsConfig();
        config.setStatus(DmsStatus.ACTIVE);
        when(dmsConfigRepository.findByOwnerIdForUpdate(owner.getId())).thenReturn(Optional.of(config));

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), null);
        assertThrows(DmsNotTriggeredException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));

        verify(legalVerificationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Existing active request for owner -> DuplicateVerificationRequestException (409)")
    void submit_duplicateActiveRequest_conflict() throws Exception {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        stubDmsTriggered();
        when(legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(eq(owner.getId()), any()))
                .thenReturn(true);

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), null);
        assertThrows(DuplicateVerificationRequestException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));

        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    @DisplayName("Extension mismatch with magic bytes -> InvalidVerificationDocumentException (400)")
    void submit_extensionMismatch_badRequest() {
        // File name has .pdf but content is PNG magic bytes
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile mismatchFile = new MockMultipartFile("file", "fake.pdf", "application/pdf", pngBytes);

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), null);
        assertThrows(InvalidVerificationDocumentException.class, () -> service.submit(assignmentId, LOGIN, mismatchFile, req));
    }

    @Test
    @DisplayName("Downstream database exception triggers orphan file cleanup")
    void submit_dbErrorCleansUpOrphanFile() throws Exception {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        stubDmsTriggered();
        when(legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(eq(owner.getId()), any()))
                .thenReturn(false);
        when(vaultRepository.findById(vault1.getId())).thenReturn(Optional.of(vault1));

        when(cryptoService.encryptBytes(any())).thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
        when(fileStorageService.storeFile(any(), any())).thenReturn("/files/temp.pdf");
        when(cryptoService.encrypt("/files/temp.pdf")).thenReturn("ENC_PATH");
        when(legalVerificationRequestRepository.saveAndFlush(any(LegalVerificationRequest.class)))
                .thenThrow(new IllegalStateException("database error"));

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), null);
        assertThrows(IllegalStateException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));

        verify(fileStorageService).deleteFile("/files/temp.pdf");
    }

    @Test
    @DisplayName("Assignment not found -> ExecutorAssignmentNotFoundException (404)")
    void submit_assignmentNotFound_throws404() {
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());
        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "note");
        assertThrows(ExecutorAssignmentNotFoundException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));
    }

    @Test
    @DisplayName("Caller is not assigned executor user -> ExecutorAssignmentNotFoundException (404)")
    void submit_callerMismatch_throws404() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "note");
        assertThrows(ExecutorAssignmentNotFoundException.class, () -> service.submit(assignmentId, "other-user-uuid", pdf(), req));
    }

    @Test
    @DisplayName("Empty document file -> InvalidVerificationDocumentException (400)")
    void submit_emptyFile_badRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[0]);
        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "note");
        assertThrows(InvalidVerificationDocumentException.class, () -> service.submit(assignmentId, LOGIN, emptyFile, req));
    }

    @Test
    @DisplayName("File exceeds 10MB limit -> InvalidVerificationDocumentException (400)")
    void submit_fileExceedsLimit_badRequest() {
        byte[] largeBytes = new byte[(int) (10 * 1024 * 1024 + 1)];
        largeBytes[0] = '%'; largeBytes[1] = 'P'; largeBytes[2] = 'D'; largeBytes[3] = 'F';
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);
        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "note");
        assertThrows(InvalidVerificationDocumentException.class, () -> service.submit(assignmentId, LOGIN, largeFile, req));
    }

    @Test
    @DisplayName("Unsupported magic bytes -> InvalidVerificationDocumentException (400)")
    void submit_unsupportedMagicBytes_badRequest() {
        byte[] randomBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        MockMultipartFile invalidFile = new MockMultipartFile("file", "invalid.pdf", "application/pdf", randomBytes);
        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "note");
        assertThrows(InvalidVerificationDocumentException.class, () -> service.submit(assignmentId, LOGIN, invalidFile, req));
    }

    @Test
    @DisplayName("Vault not found in repository -> VaultException (404)")
    void submit_vaultNotFound_throws404() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));
        stubDmsTriggered();
        when(legalVerificationRequestRepository.existsByOwnerIdAndStatusIn(eq(owner.getId()), any()))
                .thenReturn(false);
        when(vaultRepository.findById(vault1.getId())).thenReturn(Optional.empty());

        CreateDeathVerificationRequest req = new CreateDeathVerificationRequest(List.of(vault1.getId()), "note");
        VaultException ex = assertThrows(VaultException.class, () -> service.submit(assignmentId, LOGIN, pdf(), req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
