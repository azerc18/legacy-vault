package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.event.AssetReverificationNeededEvent;
import com.ltld.app.legacyvault.event.ExecutorTaskCompletedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditMailListenerLifecycleTest {

    @Mock private EmailSender emailSender;
    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private MailDataReader mailData;
    @InjectMocks private AuditMailListener listener;

    private final UUID userId = UUID.randomUUID();
    private final UUID vaultId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

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
        return new AuditEvent(action, userId, vaultId, "X", entityId, null, meta);
    }

    @Test
    void login_newDevice_sendsNotice() {
        when(mailData.newDeviceOf(userId)).thenReturn(Optional.of("Safari"));

        listener.handle(event(AuditAction.LOGIN, "SUCCESS", null));

        verify(emailSender).sendNewDeviceLoginNotice("an@example.com", "An", "Safari");
    }

    @Test
    void login_knownDevice_sendsNothing() {
        when(mailData.newDeviceOf(userId)).thenReturn(Optional.empty());

        listener.handle(event(AuditAction.LOGIN, "SUCCESS", null));

        verifyNoInteractions(emailSender);
    }

    @Test
    void vaultUpdated_legacyMessage_sendsWithoutTitle() {
        Vault vault = new Vault();
        vault.setName("Kho");
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        listener.handle(event(AuditAction.VAULT_UPDATED, "SUCCESS", "Updated legacy message: bi mat"));

        verify(emailSender).sendLegacyMessageSavedNotice("an@example.com", "An", "Kho", true);
    }

    @Test
    void vaultUpdated_otherChange_sendsNothing() {
        listener.handle(event(AuditAction.VAULT_UPDATED, "SUCCESS", "Renamed vault"));

        verifyNoInteractions(emailSender);
    }

    @Test
    void assetVerified_sendsNotice() {
        when(mailData.assetName(entityId)).thenReturn(Optional.of("Gmail"));

        listener.handle(event(AuditAction.ASSET_VERIFICATION_VERIFIED, "SUCCESS", "OTP_EMAIL"));

        verify(emailSender).sendAssetVerifiedNotice("an@example.com", "An", "Gmail");
    }

    @Test
    void vaultArchived_mailsEachBeneficiary() {
        when(mailData.vaultRecipients(vaultId)).thenReturn(Optional.of(new MailDataReader.VaultRecipients("Kho", List.of(
                new MailDataReader.Recipient("a@example.com", "A"), new MailDataReader.Recipient("b@example.com", "B")))));

        listener.handle(event(AuditAction.VAULT_ARCHIVED, "SUCCESS", "claim timeout reached"));

        verify(emailSender).sendVaultArchivedNotice("a@example.com", "A", "Kho");
        verify(emailSender).sendVaultArchivedNotice("b@example.com", "B", "Kho");
    }

    @Test
    void requestReopened_mailsExecutor() {
        when(mailData.reopenedRequest(entityId))
                .thenReturn(Optional.of(new MailDataReader.ReopenedRequest("exec@example.com", "Chu")));

        listener.handle(event(AuditAction.LEGAL_REQUEST_REOPENED, "SUCCESS", "approved over 7 days"));

        verify(emailSender).sendVerificationReopenedNotice("exec@example.com", "Chu");
    }

    @Test
    void assetReverification_mailsOwner() {
        LocalDateTime expires = LocalDateTime.of(2026, 12, 1, 0, 0);

        listener.onAssetReverificationNeeded(new AssetReverificationNeededEvent(userId, vaultId, entityId, "Gmail", expires, true));

        verify(emailSender).sendAssetReverificationNotice("an@example.com", "An", "Gmail", expires, true, vaultId);
    }

    @Test
    void assetReverification_mailError_isSwallowed() {
        doThrow(new IllegalStateException("smtp")).when(emailSender)
                .sendAssetReverificationNotice(any(), any(), any(), any(), eq(false), any());

        listener.onAssetReverificationNeeded(new AssetReverificationNeededEvent(userId, vaultId, entityId, "Gmail",
                LocalDateTime.now(), false));

        verify(emailSender).sendAssetReverificationNotice(any(), any(), any(), any(), eq(false), any());
    }

    @Test
    void taskCompleted_mailsLinkedBeneficiaries() {
        when(mailData.completedTask(entityId)).thenReturn(Optional.of(new MailDataReader.CompletedTask("Kho", "Facebook",
                List.of(new MailDataReader.Recipient("a@example.com", "A")))));

        listener.onExecutorTaskCompleted(new ExecutorTaskCompletedEvent(entityId, vaultId, userId, UUID.randomUUID(), true));

        verify(emailSender).sendExecutorTaskCompletedNotice("a@example.com", "A", "Kho", "Facebook", true, vaultId);
    }

    @Test
    void taskCompleted_unknownTask_sendsNothing() {
        when(mailData.completedTask(entityId)).thenReturn(Optional.empty());

        listener.onExecutorTaskCompleted(new ExecutorTaskCompletedEvent(entityId, vaultId, userId, UUID.randomUUID(), false));

        verifyNoInteractions(emailSender);
    }
}
