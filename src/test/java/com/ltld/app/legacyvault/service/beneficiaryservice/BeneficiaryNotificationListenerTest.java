package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryNotificationListenerTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private BeneficiaryClaimRepository claimRepository;
    @Mock private EmailSender emailSender;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformTransactionManager transactionManager;

    private BeneficiaryNotificationListener listener;

    private final UUID vaultId = UUID.randomUUID();
    private final LocalDateTime deadline = LocalDateTime.now().plusDays(60);

    @BeforeEach
    void setUp() {
        listener = new BeneficiaryNotificationListener(
                vaultRepository, claimRepository, emailSender, auditLogService, transactionManager);
    }

    private User beneficiary(UserStatus status) {
        return User.builder().id(UUID.randomUUID()).fullName("An").email("an@example.com").status(status).build();
    }

    private Vault unlockedVault(UUID id, User beneficiary) {
        return Vault.builder().id(id).status(VaultStatus.UNLOCKED)
                .beneficiary(beneficiary).claimDeadlineAt(deadline).build();
    }

    private VaultsUnlockedEvent event(UUID... ids) {
        return new VaultsUnlockedEvent(UUID.randomUUID(), List.of(ids));
    }

    @Test
    void activeBeneficiary_noClaim_createsClaim_sendsEmail_setsNotifiedAt_andAudits() {
        Vault vault = unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE));
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(i -> i.getArgument(0));

        listener.onVaultsUnlocked(event(vaultId));

        ArgumentCaptor<BeneficiaryClaim> captor = ArgumentCaptor.forClass(BeneficiaryClaim.class);
        verify(claimRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        BeneficiaryClaim saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(ClaimStatus.PENDING, saved.getStatus());
        assertEquals(deadline, saved.getClaimDeadlineAt());
        assertNotNull(saved.getNotifiedAt());

        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", deadline, true);
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFIED), eq(AuditResult.SUCCESS),
                isNull(), eq("an@example.com"), eq("Vault"), eq(vaultId.toString()), anyString());
    }

    @Test
    void pendingAccount_sendsInviteVariant() {
        Vault vault = unlockedVault(vaultId, beneficiary(UserStatus.PENDING));
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(i -> i.getArgument(0));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", deadline, false);
    }

    @Test
    void existingClaimNotYetNotified_reusesClaimAndSetsNotifiedAt() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        LocalDateTime claimDeadline = deadline.minusDays(1);
        BeneficiaryClaim claim = BeneficiaryClaim.builder().vault(vault).beneficiary(user)
                .status(ClaimStatus.PENDING).claimDeadlineAt(claimDeadline).build();
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(claim));

        listener.onVaultsUnlocked(event(vaultId));

        assertNotNull(claim.getNotifiedAt());
        verify(claimRepository).save(claim);
        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", claimDeadline, true);
    }

    @Test
    void alreadyNotified_skipsEmailAndSave() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        BeneficiaryClaim claim = BeneficiaryClaim.builder().vault(vault).beneficiary(user)
                .status(ClaimStatus.PENDING).claimDeadlineAt(deadline)
                .notifiedAt(LocalDateTime.now().minusHours(1)).build();
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(claim));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), anyBoolean());
        verify(claimRepository, never()).save(any());
    }

    @Test
    void noBeneficiary_sendsNothing_andAuditsFailure() {
        Vault vault = unlockedVault(vaultId, null);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), anyBoolean());
        verify(claimRepository, never()).save(any());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_FAILED), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq("Vault"), eq(vaultId.toString()), anyString());
    }

    @Test
    void vaultNotUnlocked_isSkipped() {
        Vault vault = unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE));
        vault.setStatus(VaultStatus.ACTIVE);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), anyBoolean());
        verify(claimRepository, never()).save(any());
    }

    @Test
    void oneVaultFails_otherVaultsAreStillNotified() {
        UUID badVault = UUID.randomUUID();
        Vault goodVault = unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE));
        when(vaultRepository.findById(badVault)).thenThrow(new RuntimeException("db down"));
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(goodVault));
        when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(i -> i.getArgument(0));

        listener.onVaultsUnlocked(event(badVault, vaultId));

        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_FAILED), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq("Vault"), eq(badVault.toString()), anyString());
        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", deadline, true);
    }
}