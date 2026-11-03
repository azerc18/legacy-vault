package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.BeneficiaryClaimProgressResponse;
import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryClaimProgress;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.BeneficiaryReminderRequestedEvent;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorClaimTrackingServiceImplTest {

    private static final String LOGIN = "executor@example.com";

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private BeneficiaryClaimRepository beneficiaryClaimRepository;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private ExecutorClaimTrackingServiceImpl service;

    private final UUID assignmentId = UUID.randomUUID();
    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
    private final User executor = User.builder().id(UUID.randomUUID()).email(LOGIN).build();
    private Vault vault;

    @BeforeEach
    void setUp() {
        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault A")
                .status(VaultStatus.UNLOCKED).claimDeadlineAt(LocalDateTime.now().plusDays(10)).build();
    }

    private void stubGuardAllows() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build());
    }

    private void stubVault(List<VaultBeneficiary> beneficiaries, List<BeneficiaryClaim> claims,
                           List<AssetAllocation> allocations) {
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        when(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vault.getId())).thenReturn(beneficiaries);
        when(beneficiaryClaimRepository.findAllByVaultId(vault.getId())).thenReturn(claims);
        when(assetAllocationRepository.findByVaultId(vault.getId())).thenReturn(allocations);
    }

    private VaultBeneficiary beneficiary(String email, BeneficiaryInviteStatus status, User user) {
        return VaultBeneficiary.builder().id(UUID.randomUUID()).vault(vault).email(email).fullName(email)
                .status(status).user(user).build();
    }

    // ---------- listBeneficiaryProgress ----------

    @Test
    void list_mapsEachBeneficiaryToProgressStatus_andMasksEmail() {
        User claimedUser = User.builder().id(UUID.randomUUID()).build();
        VaultBeneficiary pending = beneficiary("pending@example.com", BeneficiaryInviteStatus.INVITED, null);
        VaultBeneficiary accepted = beneficiary("accepted@example.com", BeneficiaryInviteStatus.ACCEPTED, null);
        VaultBeneficiary claimed = beneficiary("claimed@example.com", BeneficiaryInviteStatus.VERIFIED, claimedUser);
        VaultBeneficiary declined = beneficiary("declined@example.com", BeneficiaryInviteStatus.DECLINED, null);
        VaultBeneficiary fellBack = beneficiary("fellback@example.com", BeneficiaryInviteStatus.DECLINED, null);
        BeneficiaryClaim claim = BeneficiaryClaim.builder().vault(vault).beneficiary(claimedUser)
                .status(ClaimStatus.CLAIMED).claimedAt(LocalDateTime.now().minusDays(1)).build();
        AssetAllocation moved = AssetAllocation.builder().beneficiary(fellBack).effectiveBeneficiary(pending).build();
        AssetAllocation stillOwn = AssetAllocation.builder().beneficiary(declined).build();
        stubGuardAllows();
        stubVault(List.of(pending, accepted, claimed, declined, fellBack), List.of(claim), List.of(moved, stillOwn));

        List<BeneficiaryClaimProgressResponse> rows = service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN);

        assertThat(rows).extracting(BeneficiaryClaimProgressResponse::getStatus).containsExactly(
                BeneficiaryClaimProgress.PENDING, BeneficiaryClaimProgress.ACCEPTED, BeneficiaryClaimProgress.ACCEPTED,
                BeneficiaryClaimProgress.DECLINED, BeneficiaryClaimProgress.FALLBACK_APPLIED);
        assertThat(rows).extracting(BeneficiaryClaimProgressResponse::isCanRemind)
                .containsExactly(true, false, false, false, false);
        assertEquals("pe*****@example.com", rows.get(0).getMaskedEmail());
        assertEquals(claim.getClaimedAt(), rows.get(2).getClaimedAt());
    }

    @Test
    void list_remindedWithin24h_cannotRemindAndShowsNextAllowedTime() {
        LocalDateTime last = LocalDateTime.now().minusHours(2);
        VaultBeneficiary pending = beneficiary("p@example.com", BeneficiaryInviteStatus.INVITED, null);
        pending.setLastReminderAt(last);
        stubGuardAllows();
        stubVault(List.of(pending), List.of(), List.of());

        BeneficiaryClaimProgressResponse row = service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN).get(0);

        assertThat(row.isCanRemind()).isFalse();
        assertEquals(last.plusHours(24), row.getNextReminderAllowedAt());
    }

    @Test
    void list_noBeneficiaries_returnsEmptyList() {
        stubGuardAllows();
        stubVault(List.of(), List.of(), List.of());

        assertThat(service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN)).isEmpty();
    }

    @Test
    void list_vaultOfAnotherOwner_throws404() {
        vault.setOwner(User.builder().id(UUID.randomUUID()).build());
        stubGuardAllows();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(vaultBeneficiaryRepository);
    }

    @Test
    void list_vaultNotHandedOverYet_throws404() {
        vault.setStatus(VaultStatus.ACTIVE);
        stubGuardAllows();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void list_unknownVault_throws404() {
        stubGuardAllows();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void list_executorNotActive_propagatesGuardErrorAndReadsNothing() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorNotActiveException("not active"));

        assertThrows(ExecutorNotActiveException.class,
                () -> service.listBeneficiaryProgress(assignmentId, vault.getId(), LOGIN));
        verifyNoInteractions(vaultRepository, vaultBeneficiaryRepository);
    }

    // ---------- sendReminder ----------

    @Test
    void remind_pendingBeneficiary_recordsReminderPublishesEventAndAudits() {
        VaultBeneficiary pending = beneficiary("p@example.com", BeneficiaryInviteStatus.INVITED, null);
        stubGuardAllows();
        stubVault(List.of(pending), List.of(), List.of());
        when(vaultBeneficiaryRepository.markReminded(eq(pending.getId()), any(), any())).thenReturn(1);

        service.sendReminder(assignmentId, vault.getId(), pending.getId(), LOGIN);

        verify(eventPublisher).publishEvent(new BeneficiaryReminderRequestedEvent(pending.getId()));
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_REMINDER_SENT), eq(AuditResult.SUCCESS),
                eq(executor.getId()), eq(LOGIN), eq(vault.getId()), eq("VaultBeneficiary"),
                eq(pending.getId().toString()), any());
    }

    @Test
    void remind_within24h_throws429AndSendsNothing() {
        VaultBeneficiary pending = beneficiary("p@example.com", BeneficiaryInviteStatus.INVITED, null);
        stubGuardAllows();
        stubVault(List.of(pending), List.of(), List.of());
        when(vaultBeneficiaryRepository.markReminded(eq(pending.getId()), any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendReminder(assignmentId, vault.getId(), pending.getId(), LOGIN));

        assertEquals(ErrorCode.REMINDER_TOO_SOON, ex.getErrorCode());
        verifyNoInteractions(eventPublisher, auditLogService);
    }

    @Test
    void remind_beneficiaryAlreadyResponded_throws409() {
        VaultBeneficiary accepted = beneficiary("a@example.com", BeneficiaryInviteStatus.ACCEPTED, null);
        stubGuardAllows();
        stubVault(List.of(accepted), List.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendReminder(assignmentId, vault.getId(), accepted.getId(), LOGIN));

        assertEquals(ErrorCode.CLAIM_ALREADY_RESPONDED, ex.getErrorCode());
        verify(vaultBeneficiaryRepository, never()).markReminded(any(), any(), any());
    }

    @Test
    void remind_beneficiaryOfAnotherVault_throws404() {
        stubGuardAllows();
        stubVault(List.of(beneficiary("p@example.com", BeneficiaryInviteStatus.INVITED, null)), List.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendReminder(assignmentId, vault.getId(), UUID.randomUUID(), LOGIN));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(vaultBeneficiaryRepository, never()).markReminded(any(), any(), any());
    }

    @Test
    void remind_vaultAlreadyClaimed_throws409() {
        vault.setStatus(VaultStatus.CLAIMED);
        stubGuardAllows();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendReminder(assignmentId, vault.getId(), UUID.randomUUID(), LOGIN));

        assertEquals(ErrorCode.VAULT_STATE_CONFLICT, ex.getErrorCode());
        verifyNoInteractions(vaultBeneficiaryRepository, eventPublisher);
    }

    @Test
    void remind_claimDeadlinePassed_throws409() {
        vault.setClaimDeadlineAt(LocalDateTime.now().minusMinutes(1));
        stubGuardAllows();
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendReminder(assignmentId, vault.getId(), UUID.randomUUID(), LOGIN));

        assertEquals(ErrorCode.VAULT_STATE_CONFLICT, ex.getErrorCode());
    }

    // ---------- maskEmail ----------

    @Test
    void maskEmail_handlesShortLongAndInvalidValues() {
        assertEquals("ng*******@gmail.com", ExecutorClaimTrackingServiceImpl.maskEmail("nguyenvan@gmail.com"));
        assertEquals("a**@x.vn", ExecutorClaimTrackingServiceImpl.maskEmail("abc@x.vn"));
        assertEquals("a@x.vn", ExecutorClaimTrackingServiceImpl.maskEmail("a@x.vn"));
        assertEquals("***", ExecutorClaimTrackingServiceImpl.maskEmail("no-at-sign"));
        assertEquals(null, ExecutorClaimTrackingServiceImpl.maskEmail(null));
    }
}
