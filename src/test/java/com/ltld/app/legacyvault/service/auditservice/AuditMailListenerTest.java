package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditMailListenerTest {

    @Mock private EmailSender emailSender;
    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @InjectMocks private AuditMailListener listener;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("an@example.com");
        user.setFullName("An");
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private AuditEvent event(AuditAction action, String result, String detail) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result);
        if (detail != null) {
            meta.put("detail", detail);
        }
        return new AuditEvent(action, userId, null, null, null, null, meta);
    }

    @Test
    void emailVerified_sendsWelcome() {
        listener.handle(event(AuditAction.EMAIL_VERIFIED, "SUCCESS", null));

        verify(emailSender).sendWelcomeEmail("an@example.com", "An");
    }

    @Test
    void passwordResetSuccess_sendsPasswordChangedNotice() {
        listener.handle(event(AuditAction.PASSWORD_RESET_SUCCESS, "SUCCESS", null));

        verify(emailSender).sendPasswordChangedNotice("an@example.com", "An");
    }

    @Test
    void failedResult_sendsNothing() {
        listener.handle(event(AuditAction.PASSWORD_RESET_SUCCESS, "FAILURE", null));

        verifyNoInteractions(emailSender);
    }

    @Test
    void accountLocked_afterFailedAttempts_sendsLockedNoticeWithMinutes() {
        String until = Instant.now().plusSeconds(15 * 60).toString();

        listener.handle(event(AuditAction.ACCOUNT_LOCKED, "FAILURE", "locked until " + until + " after 5 failed attempts"));

        verify(emailSender).sendAccountLockedNotice("an@example.com", "An", 15);
    }

    @Test
    void accountLocked_attemptWhileAlreadyLocked_sendsNothing() {
        listener.handle(event(AuditAction.ACCOUNT_LOCKED, "FAILURE", "account locked"));

        verify(emailSender, never()).sendAccountLockedNotice(any(), any(), anyInt());
    }

    @Test
    void dmsCheckin_sendsConfirmationWithNextDeadline() {
        listener.handle(event(AuditAction.DMS_CHECKIN, "SUCCESS",
                "owner=" + userId + ", nextCheckinDueAt=2026-12-01T10:30"));

        verify(emailSender).sendDmsCheckinConfirmation(eq("an@example.com"), eq("An"),
                eq(LocalDateTime.of(2026, 12, 1, 10, 30)));
    }

    @Test
    void unknownActor_sendsNothing() {
        AuditEvent e = new AuditEvent(AuditAction.EMAIL_VERIFIED, UUID.randomUUID(), null, null, null, null,
                Map.of("result", "SUCCESS"));

        listener.handle(e);

        verifyNoInteractions(emailSender);
    }

    @Test
    void parsersToleratBadInput() {
        assertThat(AuditMailListener.nextCheckin("nextCheckinDueAt=not-a-date")).isNull();
        assertThat(AuditMailListener.nextCheckin(null)).isNull();
        assertThat(AuditMailListener.lockMinutes("locked until garbage")).isEqualTo(15);
    }

    private AuditEvent eventFor(AuditAction action, UUID actor, UUID vaultId, String entityType, UUID entityId,
                                String detail) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", "SUCCESS");
        if (detail != null) {
            meta.put("detail", detail);
        }
        return new AuditEvent(action, actor, vaultId, entityType, entityId, null, meta);
    }

    @Test
    void vaultDeleted_mailsOwnerWithVaultName() {
        listener.handle(event(AuditAction.VAULT_DELETED, "SUCCESS", "name=Hồ sơ A"));

        verify(emailSender).sendVaultDeletedNotice("an@example.com", "An", "Hồ sơ A");
    }

    @Test
    void dmsConfigured_parsesSettings() {
        listener.handle(event(AuditAction.DMS_CONFIGURED, "SUCCESS",
                "ownerId=" + userId + ", created=true, checkinIntervalDays=30, gracePeriodDays=7, reminderCount=3"));

        verify(emailSender).sendDmsConfiguredNotice("an@example.com", "An", 30, 7, 3);
    }

    @Test
    void dmsSuspendedByAdmin_mailsTheOwnerNotTheAdmin() {
        UUID adminId = UUID.randomUUID();

        listener.handle(eventFor(AuditAction.DMS_SUSPENDED, adminId, null, "DmsConfig", UUID.randomUUID(),
                "ownerId=" + userId + ", reason=maintenance"));

        verify(emailSender).sendDmsAdminActionNotice("an@example.com", "An", true, "maintenance");
    }

    @Test
    void dmsResumedByAdmin_hasNoReason() {
        listener.handle(eventFor(AuditAction.DMS_RESUMED, UUID.randomUUID(), null, "DmsConfig", UUID.randomUUID(),
                "ownerId=" + userId + ", nextCheckinDueAt=2026-12-01T10:30"));

        verify(emailSender).sendDmsAdminActionNotice("an@example.com", "An", false, null);
    }

    @Test
    void claimCompleted_mailsBeneficiaryWithVaultName() {
        UUID vaultId = UUID.randomUUID();
        Vault vault = new Vault();
        vault.setName("Hồ sơ A");
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        listener.handle(eventFor(AuditAction.CLAIM_COMPLETED, userId, vaultId, "Vault", vaultId, "unviewedCount=0"));

        verify(emailSender).sendClaimCompletedNotice("an@example.com", "An", "Hồ sơ A");
    }

    @Test
    void adminLocksUser_mailsTheTargetWithReason() {
        listener.handle(eventFor(AuditAction.RBAC_CHANGE, UUID.randomUUID(), null, "User", userId,
                "STATUS ACTIVE->LOCKED reason=spam"));

        verify(emailSender).sendAccountStatusNotice("an@example.com", "An", true, "spam");
    }

    @Test
    void adminUnlocksUser_mailsTheTarget() {
        listener.handle(eventFor(AuditAction.RBAC_CHANGE, UUID.randomUUID(), null, "User", userId,
                "STATUS LOCKED->ACTIVE"));

        verify(emailSender).sendAccountStatusNotice("an@example.com", "An", false, null);
    }

    @Test
    void statusChangeNotInvolvingLock_sendsNothing() {
        listener.handle(eventFor(AuditAction.RBAC_CHANGE, UUID.randomUUID(), null, "User", userId,
                "STATUS PENDING->ACTIVE"));

        verifyNoInteractions(emailSender);
    }

    @Test
    void roleGrantAndRevoke_mailTheTarget() {
        listener.handle(eventFor(AuditAction.RBAC_CHANGE, UUID.randomUUID(), null, "User", userId, "GRANT VERIFIER"));
        listener.handle(eventFor(AuditAction.RBAC_CHANGE, UUID.randomUUID(), null, "User", userId, "REVOKE OWNER"));

        verify(emailSender).sendRoleChangedNotice("an@example.com", "An", true, "VERIFIER");
        verify(emailSender).sendRoleChangedNotice("an@example.com", "An", false, "OWNER");
    }

    @Test
    void roleDefinitionChange_isNotAUserMail() {
        listener.handle(eventFor(AuditAction.RBAC_CHANGE, UUID.randomUUID(), null, "Role", UUID.randomUUID(),
                "GRANT x"));

        verifyNoInteractions(emailSender);
    }
}
