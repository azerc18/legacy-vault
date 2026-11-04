package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.dto.executordto.UpdateExecutorTaskRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorTaskServiceImplTest {

    private static final String LOGIN = "executor@example.com";

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private ExecutorTaskRepository executorTaskRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private ExecutorTaskServiceImpl service;

    private final UUID assignmentId = UUID.randomUUID();
    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
    private final User executor = User.builder().id(UUID.randomUUID()).email(LOGIN).build();
    private Vault vault;

    @BeforeEach
    void setUp() {
        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault A").status(VaultStatus.UNLOCKED).build();
    }

    private void stubGuardAllows() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build());
    }

    private DigitalAsset asset(TransferMechanism mechanism, String publicJson) {
        return DigitalAsset.builder().id(UUID.randomUUID()).vault(vault).assetType(AssetType.SOCIAL_MEDIA)
                .transferMechanism(mechanism).publicFieldsJson(publicJson).status(AssetStatus.ACTIVE).build();
    }

    private ExecutorTask task(ExecutorTaskStatus status) {
        return ExecutorTask.builder().id(UUID.randomUUID()).vault(vault)
                .asset(asset(TransferMechanism.EXECUTOR_DISPOSITION, null))
                .platform("Facebook").accountHandle("owner.fb").wish(ExecutorTaskWish.DELETE).status(status).build();
    }

    private static UpdateExecutorTaskRequest request(ExecutorTaskStatus status, String note) {
        UpdateExecutorTaskRequest r = new UpdateExecutorTaskRequest();
        r.setStatus(status);
        r.setNote(note);
        return r;
    }

    // ---------- generateForVaults ----------

    @Test
    void generate_createsTaskOnlyForDispositionAssetsWithValidWish() {
        DigitalAsset social = asset(TransferMechanism.EXECUTOR_DISPOSITION,
                "{\"platform\":\"Facebook\",\"usernameOrHandle\":\"owner.fb\",\"wish\":\"MEMORIALIZE\"}");
        DigitalAsset bank = asset(TransferMechanism.LEGAL_CLAIM_DOSSIER, "{\"institutionName\":\"VCB\"}");
        DigitalAsset noWish = asset(TransferMechanism.EXECUTOR_DISPOSITION, "{\"platform\":\"Gmail\"}");
        when(digitalAssetRepository.findByVaultIdInAndStatus(List.of(vault.getId()), AssetStatus.ACTIVE))
                .thenReturn(List.of(social, bank, noWish));
        when(executorTaskRepository.findAssetIdsWithTask(anyCollection())).thenReturn(Set.of());

        int created = service.generateForVaults(List.of(vault.getId()));

        assertEquals(1, created);
        ArgumentCaptor<ExecutorTask> saved = ArgumentCaptor.forClass(ExecutorTask.class);
        verify(executorTaskRepository).save(saved.capture());
        assertEquals("Facebook", saved.getValue().getPlatform());
        assertEquals("owner.fb", saved.getValue().getAccountHandle());
        assertEquals(ExecutorTaskWish.MEMORIALIZE, saved.getValue().getWish());
        assertEquals(ExecutorTaskStatus.TODO, saved.getValue().getStatus());
    }

    @Test
    void generate_assetAlreadyHasTask_isSkipped() {
        DigitalAsset social = asset(TransferMechanism.EXECUTOR_DISPOSITION, "{\"wish\":\"DELETE\"}");
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE))).thenReturn(List.of(social));
        when(executorTaskRepository.findAssetIdsWithTask(anyCollection())).thenReturn(Set.of(social.getId()));

        assertEquals(0, service.generateForVaults(List.of(vault.getId())));
        verify(executorTaskRepository, never()).save(any());
    }

    @Test
    void generate_emptyOrNullVaultIds_readsNothing() {
        assertEquals(0, service.generateForVaults(List.of()));
        assertEquals(0, service.generateForVaults(null));
        verifyNoInteractions(digitalAssetRepository, executorTaskRepository);
    }

    @Test
    void parseWish_rejectsUnknownAndBlankValues() {
        assertEquals(ExecutorTaskWish.TRANSFER_DATA, ExecutorTaskServiceImpl.parseWish(" TRANSFER_DATA "));
        assertNull(ExecutorTaskServiceImpl.parseWish("SELL"));
        assertNull(ExecutorTaskServiceImpl.parseWish(""));
        assertNull(ExecutorTaskServiceImpl.parseWish(null));
    }

    @Test
    void helpUrl_facebookMemorializeHasOwnForm_unknownPlatformIsNull() {
        String memorialize = ExecutorTaskServiceImpl.helpUrl("Facebook", ExecutorTaskWish.MEMORIALIZE);
        String delete = ExecutorTaskServiceImpl.helpUrl("Facebook", ExecutorTaskWish.DELETE);
        assertNotNull(memorialize);
        assertThat(memorialize).isNotEqualTo(delete);
        assertNull(ExecutorTaskServiceImpl.helpUrl("Other", ExecutorTaskWish.DELETE));
        assertNull(ExecutorTaskServiceImpl.helpUrl(null, ExecutorTaskWish.DELETE));
    }

    // ---------- listForExecutor ----------

    @Test
    void listForExecutor_onlyHandedOverVaults_andBackfillsTasks() {
        Vault active = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Not yet").status(VaultStatus.ACTIVE).build();
        stubGuardAllows();
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of(vault, active));
        when(digitalAssetRepository.findByVaultIdInAndStatus(List.of(vault.getId()), AssetStatus.ACTIVE))
                .thenReturn(List.of());
        ExecutorTask t = task(ExecutorTaskStatus.TODO);
        when(executorTaskRepository.findByVaultIds(List.of(vault.getId()))).thenReturn(List.of(t));

        List<ExecutorTaskResponse> rows = service.listForExecutor(assignmentId, LOGIN);

        assertThat(rows).singleElement().satisfies(r -> {
            assertEquals(t.getId(), r.getId());
            assertEquals("Vault A", r.getVaultName());
            assertNotNull(r.getHelpUrl());
        });
    }

    @Test
    void listForExecutor_noHandedOverVault_returnsEmpty() {
        stubGuardAllows();
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of());

        assertThat(service.listForExecutor(assignmentId, LOGIN)).isEmpty();
        verifyNoInteractions(executorTaskRepository);
    }

    @Test
    void listForExecutor_executorNotActive_propagatesAndReadsNothing() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorNotActiveException("not active"));

        assertThrows(ExecutorNotActiveException.class, () -> service.listForExecutor(assignmentId, LOGIN));
        verifyNoInteractions(vaultRepository, executorTaskRepository);
    }

    // ---------- update ----------

    @Test
    void update_toDone_setsCompletedAtTrimsNoteAndAudits() {
        ExecutorTask t = task(ExecutorTaskStatus.DOING);
        stubGuardAllows();
        when(executorTaskRepository.findById(t.getId())).thenReturn(Optional.of(t));
        when(executorTaskRepository.saveAndFlush(t)).thenReturn(t);

        ExecutorTaskResponse r = service.update(assignmentId, t.getId(),
                request(ExecutorTaskStatus.DONE, "  Đã gửi yêu cầu #123  "), LOGIN);

        assertEquals(ExecutorTaskStatus.DONE, r.getStatus());
        assertEquals("Đã gửi yêu cầu #123", r.getNote());
        assertNotNull(t.getCompletedAt());
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_TASK_UPDATED), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(LOGIN), eq(vault.getId()), eq("ExecutorTask"), eq(t.getId().toString()),
                eq("DOING -> DONE"));
    }

    @Test
    void update_backFromDone_clearsCompletedAtAndBlankNote() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setCompletedAt(LocalDateTime.now().minusDays(1));
        t.setNote("cũ");
        stubGuardAllows();
        when(executorTaskRepository.findById(t.getId())).thenReturn(Optional.of(t));
        when(executorTaskRepository.saveAndFlush(t)).thenReturn(t);

        service.update(assignmentId, t.getId(), request(ExecutorTaskStatus.DOING, "   "), LOGIN);

        assertNull(t.getCompletedAt());
        assertNull(t.getNote());
    }

    @Test
    void update_taskOfAnotherOwner_throws404() {
        ExecutorTask t = task(ExecutorTaskStatus.TODO);
        t.getVault().setOwner(User.builder().id(UUID.randomUUID()).build());
        stubGuardAllows();
        when(executorTaskRepository.findById(t.getId())).thenReturn(Optional.of(t));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(assignmentId, t.getId(), request(ExecutorTaskStatus.DONE, null), LOGIN));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(executorTaskRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_unknownTask_throws404() {
        UUID taskId = UUID.randomUUID();
        stubGuardAllows();
        when(executorTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(assignmentId, taskId, request(ExecutorTaskStatus.DONE, null), LOGIN));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void update_archivedVault_throws409() {
        ExecutorTask t = task(ExecutorTaskStatus.TODO);
        vault.setStatus(VaultStatus.ARCHIVED_LOCKED);
        stubGuardAllows();
        when(executorTaskRepository.findById(t.getId())).thenReturn(Optional.of(t));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(assignmentId, t.getId(), request(ExecutorTaskStatus.DONE, null), LOGIN));
        assertEquals(ErrorCode.VAULT_STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void update_missingStatusOrPayload_throws400() {
        stubGuardAllows();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(assignmentId, UUID.randomUUID(), request(null, "x"), LOGIN));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertThrows(BusinessException.class, () -> service.update(assignmentId, UUID.randomUUID(), null, LOGIN));
        verify(executorTaskRepository, never()).findById(any());
    }

    @Test
    void update_noteTooLong_throws400() {
        ExecutorTask t = task(ExecutorTaskStatus.TODO);
        stubGuardAllows();
        when(executorTaskRepository.findById(t.getId())).thenReturn(Optional.of(t));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(assignmentId, t.getId(),
                request(ExecutorTaskStatus.DOING, "a".repeat(1001)), LOGIN));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(executorTaskRepository, never()).saveAndFlush(any());
    }

    // ---------- listForBeneficiary ----------

    @Test
    void listForBeneficiary_beneficiaryOfVault_seesTasks() {
        UUID userId = UUID.randomUUID();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), userId))
                .thenReturn(Optional.of(VaultBeneficiary.builder().build()));
        when(executorTaskRepository.findByVaultIds(List.of(vault.getId()))).thenReturn(List.of(task(ExecutorTaskStatus.DONE)));

        assertThat(service.listForBeneficiary(vault.getId(), userId)).hasSize(1);
    }

    @Test
    void listForBeneficiary_notBeneficiary_throws404() {
        UUID userId = UUID.randomUUID();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), userId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listForBeneficiary(vault.getId(), userId));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(executorTaskRepository);
    }

    @Test
    void listForBeneficiary_vaultNotHandedOver_throws404() {
        vault.setStatus(VaultStatus.ACTIVE);
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listForBeneficiary(vault.getId(), UUID.randomUUID()));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(vaultBeneficiaryRepository, times(0)).findByVaultIdAndUserId(any(), any());
    }
}
