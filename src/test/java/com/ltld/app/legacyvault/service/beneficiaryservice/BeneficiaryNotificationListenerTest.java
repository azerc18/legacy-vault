package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryNotificationListenerTest {

    private static final int MAX = BeneficiaryNotificationListener.MAX_NOTIFY_ATTEMPTS;

    @Mock private VaultRepository vaultRepository;
    @Mock private BeneficiaryClaimRepository claimRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private EmailSender emailSender;
    @Mock private ExecutorNotificationService executorNotificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformTransactionManager transactionManager;

    private BeneficiaryNotificationListener listener;

    private final UUID vaultId = UUID.randomUUID();
    private final UUID claimId = UUID.randomUUID();
    private final LocalDateTime deadline = LocalDateTime.now().plusDays(60);
    private final User owner = User.builder().id(UUID.randomUUID()).fullName("Bình")
            .email("binh@example.com").status(UserStatus.ACTIVE).build();

    // Claim "đã lưu" mà mock repository trả về, để test kiểm tra được notifiedAt / notifyAttempts
    private BeneficiaryClaim storedClaim;

    @BeforeEach
    void setUp() {
        listener = new BeneficiaryNotificationListener(vaultRepository, claimRepository,
                executorAssignmentRepository, emailSender, executorNotificationService,
                auditLogService, transactionManager);
    }

    private User beneficiary(UserStatus status) {
        return User.builder().id(UUID.randomUUID()).fullName("An").email("an@example.com").status(status).build();
    }

    private Vault unlockedVault(UUID id, User beneficiary) {
        return Vault.builder().id(id).name("Hồ sơ A").owner(owner).status(VaultStatus.UNLOCKED)
                .beneficiary(beneficiary).claimDeadlineAt(deadline).build();
    }

    private VaultsUnlockedEvent event(UUID... ids) {
        return new VaultsUnlockedEvent(UUID.randomUUID(), List.of(ids));
    }

    private ExecutorAssignment executor(String email, ExecutorStatus status) {
        return ExecutorAssignment.builder().id(UUID.randomUUID()).owner(owner)
                .executorUser(User.builder().id(UUID.randomUUID()).email(email).build())
                .executorType(ExecutorType.PRIMARY).status(status).build();
    }

    // Chưa có claim: save gán id và nhớ lại claim; findById trả đúng claim đó
    private void stubNewClaimPersistence() {
        lenient().when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.empty());
        stubSaveAndFindById();
    }

    private void stubExistingClaim(BeneficiaryClaim claim) {
        storedClaim = claim;
        lenient().when(claimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(claim));
        stubSaveAndFindById();
    }

    private void stubSaveAndFindById() {
        lenient().when(claimRepository.save(any(BeneficiaryClaim.class))).thenAnswer(i -> {
            BeneficiaryClaim c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(claimId);
            }
            storedClaim = c;
            return c;
        });
        lenient().when(claimRepository.findById(claimId)).thenAnswer(i -> Optional.ofNullable(storedClaim));
    }

    private void stubRetryQuery(LocalDateTime now, UUID... ids) {
        when(vaultRepository.findVaultIdsToNotify(now,
                now.minus(BeneficiaryNotificationListener.RETRY_GRACE),
                now.minus(BeneficiaryNotificationListener.retryDelay(1)),
                MAX,
                PageRequest.of(0, BeneficiaryNotificationListener.RETRY_BATCH_SIZE))).thenReturn(List.of(ids));
    }

    private BeneficiaryClaim claim(Vault vault, User user, int attempts, LocalDateTime notifiedAt) {
        return claim(vault, user, attempts, notifiedAt, null);
    }

    private BeneficiaryClaim claim(Vault vault, User user, int attempts, LocalDateTime notifiedAt,
                                   LocalDateTime lastAttemptAt) {
        return BeneficiaryClaim.builder().id(claimId).vault(vault).beneficiary(user)
                .status(ClaimStatus.PENDING).claimDeadlineAt(deadline)
                .notifyAttempts(attempts).notifiedAt(notifiedAt).lastNotifyAttemptAt(lastAttemptAt).build();
    }

    private void mailFails() {
        doThrow(new MailSendException("smtp down")).when(emailSender)
                .sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void activeBeneficiary_sendsMailThenMarksNotified_andAudits() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE))));
        stubNewClaimPersistence();

        listener.onVaultsUnlocked(event(vaultId));

        assertEquals(ClaimStatus.PENDING, storedClaim.getStatus());
        assertEquals(deadline, storedClaim.getClaimDeadlineAt());
        assertEquals(1, storedClaim.getNotifyAttempts());
        assertNotNull(storedClaim.getLastNotifyAttemptAt());
        assertNotNull(storedClaim.getNotifiedAt());

        // M1/m1: gửi mail trước, sau đó mới đọc lại claim để ghi notifiedAt
        InOrder order = inOrder(emailSender, claimRepository);
        order.verify(emailSender).sendVaultUnlockedNotification(
                "an@example.com", "An", "Bình", "Hồ sơ A", deadline, true);
        order.verify(claimRepository).findById(claimId);

        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFIED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), eq(vaultId), eq("Vault"), eq(vaultId.toString()),
                contains("recipient=an@example.com"));
    }

    @Test
    void mailFails_notifiedAtStaysNull_attemptCounted_andAuditsFailure() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE))));
        stubNewClaimPersistence();
        mailFails();

        listener.onVaultsUnlocked(event(vaultId));

        assertNull(storedClaim.getNotifiedAt());
        assertEquals(1, storedClaim.getNotifyAttempts());
        verify(claimRepository, never()).findById(any());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_FAILED), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq(vaultId), eq("Vault"), eq(vaultId.toString()),
                contains("send failed (attempt 1/"));
        verify(auditLogService, never()).log(eq(AuditAction.BENEFICIARY_NOTIFIED), any(), any(), any(),
                any(), any(), any(), any());
        // Chưa phải lần thử cuối: không bỏ cuộc, không báo Executor
        verify(auditLogService, never()).log(eq(AuditAction.BENEFICIARY_NOTIFY_GAVE_UP), any(), any(), any(),
                any(), any(), any(), any());
        verify(executorAssignmentRepository, never()).findAllByOwnerId(any());
    }

    @Test
    void pendingAccount_sendsInviteVariant() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault(vaultId, beneficiary(UserStatus.PENDING))));
        stubNewClaimPersistence();

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A", deadline, false);
    }

    @Test
    void existingClaimNotYetNotified_isReused_andAttemptIncremented() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        stubExistingClaim(claim(vault, user, 2, null));

        listener.onVaultsUnlocked(event(vaultId));

        assertEquals(3, storedClaim.getNotifyAttempts());
        assertNotNull(storedClaim.getNotifiedAt());
        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A", deadline, true);
    }

    @Test
    void alreadyNotified_skips() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        stubExistingClaim(claim(vault, user, 1, LocalDateTime.now().minusHours(1)));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void attemptsExhausted_skips() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        stubExistingClaim(claim(vault, user, MAX, null));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
        assertEquals(MAX, storedClaim.getNotifyAttempts());
    }

    @Test
    void noBeneficiary_sendsNothing_andAuditsFailure() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault(vaultId, null)));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
        verify(claimRepository, never()).save(any());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_FAILED), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq(vaultId), eq("Vault"), eq(vaultId.toString()), anyString());
    }

    @Test
    void missingClaimDeadline_sendsNothing_andAuditsFailure() {
        Vault vault = unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE));
        vault.setClaimDeadlineAt(null);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
        verify(claimRepository, never()).save(any());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_FAILED), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq(vaultId), eq("Vault"), eq(vaultId.toString()), contains("claimDeadlineAt"));
    }

    @Test
    void vaultNotUnlocked_isSkipped() {
        Vault vault = unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE));
        vault.setStatus(VaultStatus.ACTIVE);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        listener.onVaultsUnlocked(event(vaultId));

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
        verify(claimRepository, never()).save(any());
    }

    @Test
    void oneVaultFails_otherVaultsAreStillNotified() {
        UUID badVault = UUID.randomUUID();
        when(vaultRepository.findById(badVault)).thenThrow(new RuntimeException("db down"));
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE))));
        stubNewClaimPersistence();

        listener.onVaultsUnlocked(event(badVault, vaultId));

        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_FAILED), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq(badVault), eq("Vault"), eq(badVault.toString()), contains("prepare failed"));
        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A", deadline, true);
    }

    @Test
    void retryPendingNotifications_processesVaultsReturnedByQuery() {
        LocalDateTime now = LocalDateTime.now();
        stubRetryQuery(now, vaultId);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(unlockedVault(vaultId, beneficiary(UserStatus.ACTIVE))));
        stubNewClaimPersistence();

        listener.retryPendingNotifications(now);

        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A", deadline, true);
        assertNotNull(storedClaim.getNotifiedAt());
    }

    // ---------- R1: giãn lịch thử và bỏ cuộc ----------

    @Test
    void retryNotDueYet_skips() {
        LocalDateTime now = LocalDateTime.now();
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        stubRetryQuery(now, vaultId);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        // Đã thử 3 lần, lần gần nhất 3 giờ trước; sau lần thứ 3 phải chờ 4 giờ
        stubExistingClaim(claim(vault, user, 3, null, now.minusHours(3)));

        listener.retryPendingNotifications(now);

        verify(emailSender, never()).sendVaultUnlockedNotification(any(), any(), any(), any(), any(), anyBoolean());
        assertEquals(3, storedClaim.getNotifyAttempts());
    }

    @Test
    void retryDue_sendsAndRecordsAttemptTime() {
        LocalDateTime now = LocalDateTime.now();
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        stubRetryQuery(now, vaultId);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        stubExistingClaim(claim(vault, user, 3, null, now.minusHours(4)));

        listener.retryPendingNotifications(now);

        verify(emailSender).sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A", deadline, true);
        assertEquals(4, storedClaim.getNotifyAttempts());
        assertEquals(now, storedClaim.getLastNotifyAttemptAt());
        assertNotNull(storedClaim.getNotifiedAt());
    }

    @Test
    void lastAttemptFails_givesUp_auditsOnce_andNotifiesOnlyActiveExecutors() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        stubExistingClaim(claim(vault, user, MAX - 1, null));
        when(executorAssignmentRepository.findAllByOwnerId(owner.getId())).thenReturn(List.of(
                executor("active@example.com", ExecutorStatus.ACTIVE),
                executor("revoked@example.com", ExecutorStatus.REVOKED)));
        mailFails();

        listener.onVaultsUnlocked(event(vaultId));

        assertEquals(MAX, storedClaim.getNotifyAttempts());
        assertNull(storedClaim.getNotifiedAt());
        verify(executorNotificationService).send(eq("active@example.com"), contains("Hồ sơ A"));
        verify(executorNotificationService, never()).send(eq("revoked@example.com"), anyString());
        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_GAVE_UP), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq(vaultId), eq("Vault"), eq(vaultId.toString()),
                contains("executorsNotified=1/1"));
    }

    @Test
    void givesUp_evenWhenTellingExecutorFails_stillAudits() {
        User user = beneficiary(UserStatus.ACTIVE);
        Vault vault = unlockedVault(vaultId, user);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        stubExistingClaim(claim(vault, user, MAX - 1, null));
        when(executorAssignmentRepository.findAllByOwnerId(owner.getId()))
                .thenReturn(List.of(executor("active@example.com", ExecutorStatus.ACTIVE)));
        mailFails();
        doThrow(new MailSendException("smtp down")).when(executorNotificationService)
                .send(eq("active@example.com"), anyString());

        listener.onVaultsUnlocked(event(vaultId));

        verify(auditLogService).log(eq(AuditAction.BENEFICIARY_NOTIFY_GAVE_UP), eq(AuditResult.FAILURE),
                isNull(), isNull(), eq(vaultId), eq("Vault"), eq(vaultId.toString()),
                contains("executorsNotified=0/1"));
    }

    @Test
    void retryDelay_growsThenCapsAt24Hours_andCoversAboutSevenDays() {
        assertEquals(Duration.ofHours(1), BeneficiaryNotificationListener.retryDelay(1));
        assertEquals(Duration.ofHours(2), BeneficiaryNotificationListener.retryDelay(2));
        assertEquals(Duration.ofHours(4), BeneficiaryNotificationListener.retryDelay(3));
        assertEquals(Duration.ofHours(8), BeneficiaryNotificationListener.retryDelay(4));
        assertEquals(Duration.ofHours(16), BeneficiaryNotificationListener.retryDelay(5));
        assertEquals(Duration.ofHours(24), BeneficiaryNotificationListener.retryDelay(6));
        assertEquals(Duration.ofHours(24), BeneficiaryNotificationListener.retryDelay(11));

        // Tổng thời gian chờ giữa lần thử đầu và lần thử cuối
        Duration total = Duration.ZERO;
        for (int attempts = 1; attempts < MAX; attempts++) {
            total = total.plus(BeneficiaryNotificationListener.retryDelay(attempts));
        }
        assertTrue(total.compareTo(Duration.ofDays(7)) >= 0);
    }
}