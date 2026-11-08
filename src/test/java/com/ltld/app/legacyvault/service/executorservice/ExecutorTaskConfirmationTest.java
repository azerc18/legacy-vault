package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.DisputeExecutorTaskRequest;
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
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TaskConfirmationStatus;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.ExecutorTaskCompletedEvent;
import com.ltld.app.legacyvault.event.ExecutorTaskDisputedEvent;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// PR 2 (C.6): xác nhận chéo hai phía + bằng chứng mới sau phản đối
@ExtendWith(MockitoExtension.class)
class ExecutorTaskConfirmationTest {

    private static final String LOGIN = "executor@example.com";

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private ExecutorTaskRepository executorTaskRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private ExecutorTaskServiceImpl service;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID beneficiaryUserId = UUID.randomUUID();
    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
    private final User executor = User.builder().id(UUID.randomUUID()).email(LOGIN).build();
    private final VaultBeneficiary me = VaultBeneficiary.builder().id(UUID.randomUUID())
            .email("ben@example.com").status(BeneficiaryInviteStatus.ACCEPTED).build();
    private Vault vault;

    @BeforeEach
    void setUp() {
        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault A").status(VaultStatus.UNLOCKED).build();
    }

    private ExecutorTask task(ExecutorTaskStatus status) {
        DigitalAsset asset = DigitalAsset.builder().id(UUID.randomUUID()).vault(vault).assetType(AssetType.SOCIAL_MEDIA)
                .transferMechanism(TransferMechanism.EXECUTOR_DISPOSITION).status(AssetStatus.ACTIVE).build();
        return ExecutorTask.builder().id(UUID.randomUUID()).vault(vault).asset(asset).platform("Facebook")
                .accountHandle("owner.fb").wish(ExecutorTaskWish.DELETE).status(status).build();
    }

    private void stubBeneficiaryAccess(ExecutorTask t) {
        lenient().when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        lenient().when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), beneficiaryUserId))
                .thenReturn(Optional.of(me));
        lenient().when(executorTaskRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
        lenient().when(assetAllocationRepository.findAssetIdsNotHeldBy(vault.getId(), me.getId()))
                .thenReturn(List.of());
        lenient().when(executorTaskRepository.saveAndFlush(t)).thenReturn(t);
    }

    private void stubExecutorAccess(ExecutorTask t) {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build());
        when(executorTaskRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
        lenient().when(executorTaskRepository.saveAndFlush(t)).thenReturn(t);
    }

    private static DisputeExecutorTaskRequest dispute(String reason) {
        DisputeExecutorTaskRequest r = new DisputeExecutorTaskRequest();
        r.setReason(reason);
        return r;
    }

    private static UpdateExecutorTaskRequest update(ExecutorTaskStatus status, String evidenceRef) {
        UpdateExecutorTaskRequest r = new UpdateExecutorTaskRequest();
        r.setStatus(status);
        r.setEvidenceRef(evidenceRef);
        return r;
    }

    // ---------- Executor báo DONE ----------

    @Test
    void update_toDone_opensConfirmationWindowAndPublishesEvent() {
        ExecutorTask t = task(ExecutorTaskStatus.DOING);
        stubExecutorAccess(t);

        ExecutorTaskResponse r = service.update(assignmentId, t.getId(), update(ExecutorTaskStatus.DONE, "REF-1"), LOGIN);

        assertEquals(TaskConfirmationStatus.PENDING, r.getConfirmationStatus());
        assertThat(r.getConfirmDeadlineAt()).isAfter(LocalDateTime.now().plusDays(6));
        verify(eventPublisher).publishEvent(any(ExecutorTaskCompletedEvent.class));
    }

    @Test
    void update_backToDoing_clearsConfirmationWindow() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setConfirmationStatus(TaskConfirmationStatus.PENDING);
        t.setConfirmDeadlineAt(LocalDateTime.now().plusDays(3));
        stubExecutorAccess(t);

        service.update(assignmentId, t.getId(), update(ExecutorTaskStatus.DOING, null), LOGIN);

        assertNull(t.getConfirmationStatus());
        assertNull(t.getConfirmDeadlineAt());
        verify(eventPublisher, never()).publishEvent(any(ExecutorTaskCompletedEvent.class));
    }

    @Test
    void update_redoAfterDispute_withoutNewEvidence_isRejected() {
        ExecutorTask t = task(ExecutorTaskStatus.DOING);
        t.setEvidenceRef("REF-1");
        t.setConfirmationStatus(TaskConfirmationStatus.DISPUTED);
        t.setDisputedAt(LocalDateTime.now().minusHours(1));
        stubExecutorAccess(t);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(assignmentId, t.getId(), update(ExecutorTaskStatus.DONE, "REF-1"), LOGIN));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertEquals(ExecutorTaskStatus.DOING, t.getStatus());
    }

    @Test
    void update_redoAfterDispute_withNewRef_resetsWindow() {
        ExecutorTask t = task(ExecutorTaskStatus.DOING);
        t.setEvidenceRef("REF-1");
        t.setConfirmationStatus(TaskConfirmationStatus.DISPUTED);
        t.setDisputeReason("sai mã");
        t.setDisputedAt(LocalDateTime.now().minusHours(1));
        stubExecutorAccess(t);

        ExecutorTaskResponse r = service.update(assignmentId, t.getId(), update(ExecutorTaskStatus.DONE, "REF-2"), LOGIN);

        assertEquals(TaskConfirmationStatus.PENDING, r.getConfirmationStatus());
        assertNull(r.getDisputeReason());
        assertEquals("REF-2", r.getEvidenceRef());
    }

    // ---------- Beneficiary xác nhận ----------

    @Test
    void confirm_marksConfirmedAndAudits() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setConfirmationStatus(TaskConfirmationStatus.PENDING);
        stubBeneficiaryAccess(t);

        ExecutorTaskResponse r = service.confirmByBeneficiary(vault.getId(), t.getId(), beneficiaryUserId);

        assertEquals(TaskConfirmationStatus.CONFIRMED, r.getConfirmationStatus());
        assertEquals(beneficiaryUserId, t.getConfirmedBy());
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_TASK_CONFIRMED), eq(AuditResult.SUCCESS),
                eq(beneficiaryUserId), eq("ben@example.com"), eq(vault.getId()), eq("ExecutorTask"),
                eq(t.getId().toString()), eq("CONFIRMED"));
    }

    @Test
    void confirm_taskNotDone_conflict() {
        ExecutorTask t = task(ExecutorTaskStatus.DOING);
        stubBeneficiaryAccess(t);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmByBeneficiary(vault.getId(), t.getId(), beneficiaryUserId));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void confirm_notABeneficiaryOfVault_404() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        when(vaultBeneficiaryRepository.findByVaultIdAndUserId(vault.getId(), beneficiaryUserId))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmByBeneficiary(vault.getId(), t.getId(), beneficiaryUserId));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ---------- Beneficiary phản đối ----------

    @Test
    void dispute_movesTaskBackToDoing_andPublishesEvent() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setConfirmationStatus(TaskConfirmationStatus.PENDING);
        t.setConfirmDeadlineAt(LocalDateTime.now().plusDays(3));
        t.setCompletedAt(LocalDateTime.now().minusDays(1));
        stubBeneficiaryAccess(t);

        ExecutorTaskResponse r = service.disputeByBeneficiary(vault.getId(), t.getId(),
                dispute("  Mã tham chiếu không tồn tại  "), beneficiaryUserId);

        assertEquals(ExecutorTaskStatus.DOING, r.getStatus());
        assertEquals(TaskConfirmationStatus.DISPUTED, r.getConfirmationStatus());
        assertEquals("Mã tham chiếu không tồn tại", r.getDisputeReason());
        assertNull(t.getCompletedAt());
        verify(eventPublisher).publishEvent(any(ExecutorTaskDisputedEvent.class));
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_TASK_DISPUTED), eq(AuditResult.SUCCESS),
                eq(beneficiaryUserId), eq("ben@example.com"), eq(vault.getId()), eq("ExecutorTask"),
                eq(t.getId().toString()), eq("DONE -> DOING"));
    }

    @Test
    void dispute_afterDeadline_conflict() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setConfirmationStatus(TaskConfirmationStatus.PENDING);
        t.setConfirmDeadlineAt(LocalDateTime.now().minusMinutes(1));
        stubBeneficiaryAccess(t);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.disputeByBeneficiary(vault.getId(), t.getId(), dispute("Quá hạn rồi nhưng vẫn phản đối"),
                        beneficiaryUserId));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals(ExecutorTaskStatus.DONE, t.getStatus());
        verify(eventPublisher, never()).publishEvent(any(ExecutorTaskDisputedEvent.class));
    }

    @Test
    void dispute_alreadyConfirmed_conflict() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setConfirmationStatus(TaskConfirmationStatus.CONFIRMED);
        stubBeneficiaryAccess(t);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.disputeByBeneficiary(vault.getId(), t.getId(), dispute("Đã xác nhận rồi mà"), beneficiaryUserId));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void dispute_reasonTooShort_validationFailed() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.disputeByBeneficiary(vault.getId(), UUID.randomUUID(), dispute("sai"), beneficiaryUserId));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void update_afterConfirmDeadline_isRejected() {
        ExecutorTask t = task(ExecutorTaskStatus.DONE);
        t.setConfirmationStatus(TaskConfirmationStatus.PENDING);
        t.setConfirmDeadlineAt(LocalDateTime.now().minusMinutes(1));
        stubExecutorAccess(t);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(assignmentId, t.getId(), update(ExecutorTaskStatus.DOING, null), LOGIN));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        assertEquals(ExecutorTaskStatus.DONE, t.getStatus());
    }

    @Test
    void update_sameEvidenceRefAgain_doesNotAuditEvidenceTwice() {
        ExecutorTask t = task(ExecutorTaskStatus.DOING);
        t.setEvidenceRef("REF-1");
        stubExecutorAccess(t);

        service.update(assignmentId, t.getId(), update(ExecutorTaskStatus.DOING, "REF-1"), LOGIN);

        verify(auditLogService, never()).log(eq(AuditAction.EXECUTOR_EVIDENCE_ADDED), any(), any(), any(), any(),
                any(), any(), any());
    }
}
