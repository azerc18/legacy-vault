package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfirmer;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.event.BeneficiaryReminderRequestedEvent;
import com.ltld.app.legacyvault.event.ExecutorTaskCompletedEvent;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditEvent;
import com.ltld.app.legacyvault.service.auditservice.MailDataReader;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.service.executorservice.ExecutorInvitationRespondedEvent;
import com.ltld.app.legacyvault.service.executorservice.ExecutorInvitationTimeoutEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Chuông thông báo: mỗi sự kiện ghi đúng loại thông báo, đúng người nhận, đúng tham số để FE dịch. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InAppNotificationEventListenerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private DmsConfirmerRepository dmsConfirmerRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private MailDataReader mailData;
    @Mock private PlatformTransactionManager transactionManager;

    private InAppNotificationEventListener listener;

    private final User owner = user("owner@example.com", "Nguyen Van An");
    private final User beneficiary = user("bene@example.com", "Tran Thi Binh");
    private final Vault vault = Vault.builder().id(UUID.randomUUID()).name("Family vault").owner(owner).build();

    @BeforeEach
    void setUp() {
        // PlatformTransactionManager giả: TransactionTemplate chạy thẳng callback
        InAppNotifier notifier = new InAppNotifier(notificationRepository, userRepository, transactionManager);
        listener = new InAppNotificationEventListener(notifier, userRepository, vaultRepository,
                vaultBeneficiaryRepository, dmsConfirmerRepository, mailData, digitalAssetRepository);
        for (User u : List.of(owner, beneficiary)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        }
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
    }

    private static User user(String email, String name) {
        return User.builder().id(UUID.randomUUID()).email(email).fullName(name).build();
    }

    private static AuditEvent audit(AuditAction action, UUID actorId, UUID vaultId, UUID entityId, String result,
                                    String detail) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result);
        if (detail != null) {
            meta.put("detail", detail);
        }
        return new AuditEvent(action, actorId, vaultId, "Vault", entityId, null, meta);
    }

    private static String instant(int year, int month, int day, int hour) {
        return NotificationParams.toInstantText(LocalDateTime.of(year, month, day, hour, 0));
    }

    private List<Notification> saved(int count) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void beneficiaryAssigned_notifiesTheNamedUser_withVaultLinkAndParams() {
        listener.onAudit(audit(AuditAction.BENEFICIARY_ASSIGNED, owner.getId(), vault.getId(), vault.getId(),
                "SUCCESS", "beneficiaryUserId=" + beneficiary.getId()));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(beneficiary);
        assertThat(n.getType()).isEqualTo(NotificationType.BENEFICIARY_ASSIGNED);
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getReferenceType()).isEqualTo("Vault");
        assertThat(n.getReferenceId()).isEqualTo(vault.getId());
        assertThat(n.getContent()).contains("Nguyen Van An").contains("Family vault");
        assertThat(NotificationParams.parse(n.getParams()))
                .containsEntry("owner", "Nguyen Van An").containsEntry("vault", "Family vault");
    }

    @Test
    void beneficiaryAssigned_pendingRegistration_hasNoAccountSoNothingIsSaved() {
        listener.onAudit(audit(AuditAction.BENEFICIARY_ASSIGNED, owner.getId(), vault.getId(), vault.getId(),
                "SUCCESS", "pendingRegistration=true"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void beneficiaryReplaced_notifiesNewAndPreviousBeneficiary() {
        User previous = user("old@example.com", "Old One");
        when(userRepository.findById(previous.getId())).thenReturn(Optional.of(previous));

        listener.onAudit(audit(AuditAction.BENEFICIARY_REPLACED, owner.getId(), vault.getId(), vault.getId(),
                "SUCCESS", "beneficiaryUserId=" + beneficiary.getId() + ", previousUserId=" + previous.getId()));

        List<Notification> all = saved(2);
        assertThat(all).extracting(Notification::getType)
                .containsExactly(NotificationType.BENEFICIARY_ASSIGNED, NotificationType.BENEFICIARY_REMOVED);
        assertThat(all.get(1).getRecipient()).isSameAs(previous);
        // Người bị gỡ không còn quyền xem Vault nên thông báo không gắn link
        assertThat(all.get(1).getReferenceId()).isNull();
    }

    @Test
    void beneficiaryLinked_notifiesTheUserWhoJustVerifiedTheirEmail() {
        listener.onAudit(audit(AuditAction.BENEFICIARY_LINKED, beneficiary.getId(), vault.getId(), vault.getId(),
                "SUCCESS", null));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(beneficiary);
        assertThat(n.getType()).isEqualTo(NotificationType.BENEFICIARY_ASSIGNED);
    }

    @Test
    void failedOrUnrelatedAudit_isIgnoredWithoutOpeningATransaction() {
        listener.onAudit(audit(AuditAction.BENEFICIARY_ASSIGNED, owner.getId(), vault.getId(), vault.getId(),
                "FAILURE", "beneficiaryUserId=" + beneficiary.getId()));
        listener.onAudit(audit(AuditAction.LOGIN, owner.getId(), null, null, "SUCCESS", null));

        verifyNoInteractions(transactionManager, notificationRepository);
    }

    @Test
    void dmsConfirmationRequested_notifiesOwnerAndConfirmersWhoHaveAnAccount() {
        UUID configId = UUID.randomUUID();
        User confirmerUser = user("friend@example.com", "Friend");
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(confirmerUser));
        when(dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(configId)).thenReturn(List.of(
                DmsConfirmer.builder().id(UUID.randomUUID()).name("Friend").email("friend@example.com")
                        .status(DmsConfirmerStatus.ACTIVE).build(),
                DmsConfirmer.builder().id(UUID.randomUUID()).name("No account").email("stranger@example.com")
                        .status(DmsConfirmerStatus.ACTIVE).build()));

        listener.onAudit(audit(AuditAction.DMS_CONFIRMATION_REQUESTED, null, null, configId, "SUCCESS",
                "ownerId=" + owner.getId() + ", confirmers=2, deadlineAt=2026-11-13T09:00"));

        List<Notification> all = saved(2);
        Notification toConfirmer = all.get(0);
        assertThat(toConfirmer.getRecipient()).isSameAs(confirmerUser);
        assertThat(toConfirmer.getType()).isEqualTo(NotificationType.DMS_CONFIRMATION_REQUESTED);
        // Link xác nhận (token) chỉ nằm trong email: thông báo không được chứa tham chiếu nào tới nó
        assertThat(toConfirmer.getReferenceId()).isNull();
        assertThat(NotificationParams.parse(toConfirmer.getParams()))
                .containsEntry("owner", "Nguyen Van An")
                .containsEntry("deadlineAt", instant(2026, 11, 13, 9));
        Notification toOwner = all.get(1);
        assertThat(toOwner.getRecipient()).isSameAs(owner);
        assertThat(toOwner.getType()).isEqualTo(NotificationType.DMS_CONFIRMATION_PENDING);
        assertThat(NotificationParams.parse(toOwner.getParams())).containsEntry("count", "2");
    }

    @Test
    void dmsConfirmedAlive_tellsOwnerWhoConfirmedAndTheNewDate() {
        UUID confirmerId = UUID.randomUUID();
        when(dmsConfirmerRepository.findById(confirmerId)).thenReturn(Optional.of(
                DmsConfirmer.builder().id(confirmerId).name("Friend").email("friend@example.com").build()));

        listener.onAudit(audit(AuditAction.DMS_CONFIRMED_ALIVE, null, null, UUID.randomUUID(), "SUCCESS",
                "ownerId=" + owner.getId() + ", confirmerId=" + confirmerId + ", postponedUntil=2026-11-17T08:00"));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(owner);
        assertThat(n.getType()).isEqualTo(NotificationType.DMS_OWNER_CONFIRMED_SAFE);
        assertThat(NotificationParams.parse(n.getParams()))
                .containsEntry("confirmer", "Friend").containsEntry("untilAt", instant(2026, 11, 17, 8));
    }

    @Test
    void dmsSuspendedByAdmin_notifiesOwnerNotTheAdminActor() {
        listener.onAudit(audit(AuditAction.DMS_SUSPENDED, UUID.randomUUID(), null, UUID.randomUUID(), "SUCCESS",
                "ownerId=" + owner.getId() + ", reason=Owner asked by phone"));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(owner);
        assertThat(n.getType()).isEqualTo(NotificationType.DMS_PAUSED);
        assertThat(NotificationParams.parse(n.getParams())).containsEntry("reason", "Owner asked by phone");
    }

    @Test
    void assetVerified_notifiesVaultOwner_evenWhenActorIsSomeoneElse() {
        UUID assetId = UUID.randomUUID();
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(
                DigitalAsset.builder().id(assetId).assetName("Ledger").vault(vault).build()));

        listener.onAudit(audit(AuditAction.ASSET_VERIFICATION_VERIFIED, null, vault.getId(), assetId, "SUCCESS", null));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(owner);
        assertThat(n.getType()).isEqualTo(NotificationType.ASSET_VERIFIED);
        assertThat(n.getReferenceId()).isEqualTo(vault.getId());
    }

    @Test
    void executorAssignmentChanged_mapsEachMailToAssignedOrRevoked() {
        User executor = user("exec@example.com", "Exec");
        when(userRepository.findByEmail("exec@example.com")).thenReturn(Optional.of(executor));

        UUID assignmentId = UUID.randomUUID();
        listener.onExecutorAssignmentChanged(new ExecutorAssignmentChangedEvent(List.of(
                ExecutorMail.assigned("exec@example.com", "Nguyen Van An", "owner@example.com", ExecutorType.PRIMARY,
                        assignmentId),
                ExecutorMail.revokedByTimeout("exec@example.com", "Nguyen Van An", "owner@example.com", 14),
                ExecutorMail.assigned("nobody@example.com", "Nguyen Van An", "owner@example.com", ExecutorType.BACKUP,
                        UUID.randomUUID()))));

        List<Notification> all = saved(2);   // nobody@example.com chưa có tài khoản: chỉ nhận email
        assertThat(all.get(0).getType()).isEqualTo(NotificationType.EXECUTOR_ASSIGNED);
        assertThat(NotificationParams.parse(all.get(0).getParams())).containsEntry("role", "PRIMARY");
        // Lời mời: chuông mở trang đồng ý / từ chối của đúng assignment
        assertThat(all.get(0).getReferenceType()).isEqualTo(InAppNotifier.REF_EXECUTOR_ASSIGNMENT);
        assertThat(all.get(0).getReferenceId()).isEqualTo(assignmentId);
        assertThat(all.get(1).getType()).isEqualTo(NotificationType.EXECUTOR_REVOKED);
        // FE chọn câu theo variant (notifications.types.EXECUTOR_REVOKED_TIMEOUT)
        assertThat(NotificationParams.parse(all.get(1).getParams()))
                .containsEntry("variant", "TIMEOUT").containsEntry("owner", "Nguyen Van An").doesNotContainKey("reason");
    }

    @Test
    void executorInvitationAccepted_notifiesOwner() {
        UUID assignmentId = UUID.randomUUID();

        listener.onExecutorInvitationResponded(new ExecutorInvitationRespondedEvent(assignmentId, owner.getId(),
                owner.getEmail(), owner.getFullName(), "Exec", "exec@example.com", ExecutorType.PRIMARY, true, null));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(owner);
        assertThat(n.getType()).isEqualTo(NotificationType.EXECUTOR_INVITATION_ACCEPTED);
        assertThat(n.getReferenceType()).isEqualTo(InAppNotifier.REF_EXECUTOR_ASSIGNMENT);
        assertThat(n.getReferenceId()).isEqualTo(assignmentId);
        assertThat(NotificationParams.parse(n.getParams()))
                .containsEntry("executor", "Exec (exec@example.com)").containsEntry("role", "PRIMARY")
                .doesNotContainKey("reason");
    }

    @Test
    void executorInvitationDeclined_notifiesOwnerWithReason() {
        listener.onExecutorInvitationResponded(new ExecutorInvitationRespondedEvent(UUID.randomUUID(), owner.getId(),
                owner.getEmail(), owner.getFullName(), null, "exec@example.com", ExecutorType.BACKUP, false, "Busy"));

        Notification n = saved(1).get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.EXECUTOR_INVITATION_DECLINED);
        assertThat(NotificationParams.parse(n.getParams()))
                .containsEntry("executor", "exec@example.com").containsEntry("role", "BACKUP")
                .containsEntry("reason", "Busy")
                // variant -> câu dịch EXECUTOR_INVITATION_DECLINED_reason có {{reason}}
                .containsEntry("variant", "reason");
        assertThat(n.getContent()).contains("Reason: Busy");
    }

    @Test
    void executorInvitationDeclinedWithoutReason_usesPlainVariant() {
        listener.onExecutorInvitationResponded(new ExecutorInvitationRespondedEvent(UUID.randomUUID(), owner.getId(),
                owner.getEmail(), owner.getFullName(), null, "exec@example.com", ExecutorType.BACKUP, false, "  "));

        Notification n = saved(1).get(0);
        assertThat(NotificationParams.parse(n.getParams()))
                .doesNotContainKey("reason").doesNotContainKey("variant");
        assertThat(n.getContent()).doesNotContain("Reason:");
    }

    @Test
    void executorInvitationExpired_notifiesOwner() {
        UUID assignmentId = UUID.randomUUID();
        listener.onExecutorInvitationExpired(new ExecutorInvitationTimeoutEvent(
                ExecutorInvitationTimeoutEvent.Kind.EXPIRED, assignmentId, owner.getId(), owner.getEmail(),
                owner.getFullName(), "exec@example.com", "Exec", ExecutorType.PRIMARY, 7));

        Notification n = saved(1).get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.EXECUTOR_INVITATION_EXPIRED);
        assertThat(n.getReferenceId()).isEqualTo(assignmentId);
        assertThat(NotificationParams.parse(n.getParams()))
                .containsEntry("executor", "Exec (exec@example.com)")
                .containsEntry("role", "PRIMARY").containsEntry("days", "7");
    }

    @Test
    void executorInvitationReminder_doesNotNotifyOwner() {
        listener.onExecutorInvitationExpired(new ExecutorInvitationTimeoutEvent(
                ExecutorInvitationTimeoutEvent.Kind.REMINDER, UUID.randomUUID(), owner.getId(), owner.getEmail(),
                owner.getFullName(), "exec@example.com", "Exec", ExecutorType.PRIMARY, 3));

        saved(0);
    }

    @Test
    void beneficiaryReminder_usesLinkedAccountAndClaimDeadline() {
        vault.setClaimDeadlineAt(LocalDateTime.of(2026, 12, 1, 0, 0));
        UUID rowId = UUID.randomUUID();
        when(vaultBeneficiaryRepository.findById(rowId)).thenReturn(Optional.of(
                VaultBeneficiary.builder().id(rowId).vault(vault).user(beneficiary).email("typed@example.com").build()));

        listener.onBeneficiaryReminder(new BeneficiaryReminderRequestedEvent(rowId));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(beneficiary);
        assertThat(n.getType()).isEqualTo(NotificationType.CLAIM_DEADLINE_REMINDER);
        assertThat(NotificationParams.parse(n.getParams())).containsEntry("deadlineAt", instant(2026, 12, 1, 0));
    }

    @Test
    void executorTaskCompleted_notifiesLinkedBeneficiaries() {
        UUID taskId = UUID.randomUUID();
        when(mailData.completedTask(taskId)).thenReturn(Optional.of(new MailDataReader.CompletedTask("Family vault",
                "Facebook", List.of(new MailDataReader.Recipient("bene@example.com", "Tran Thi Binh")))));

        listener.onExecutorTaskCompleted(new ExecutorTaskCompletedEvent(taskId, vault.getId(), owner.getId(),
                UUID.randomUUID(), true));

        Notification n = saved(1).get(0);
        assertThat(n.getRecipient()).isSameAs(beneficiary);
        assertThat(n.getType()).isEqualTo(NotificationType.EXECUTOR_TASK_COMPLETED);
        assertThat(n.getReferenceId()).isEqualTo(vault.getId());
    }

    @Test
    void repositoryFailure_isSwallowed_soTheBusinessActionIsNeverAffected() {
        when(notificationRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onAudit(audit(AuditAction.BENEFICIARY_ASSIGNED, owner.getId(), vault.getId(),
                vault.getId(), "SUCCESS", "beneficiaryUserId=" + beneficiary.getId()))).doesNotThrowAnyException();
    }
}
