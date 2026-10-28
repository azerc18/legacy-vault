package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FR-07 / BR-03 chạy trên MySQL thật: kích hoạt DMS, leo thang Primary -> Backup, xác nhận của Executor.
 * Kịch bản S1–S9 lấy từ console dev cũ (nhánh tools/fr07-console). Mọi mốc thời gian truyền tường minh qua
 * tham số now, không sleep; scheduler tắt để chỉ test gọi service.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class DmsEscalationIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DmsEscalationIntegrationTest.class);

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private ExecutorNotificationService executorNotificationService;   // không gửi mail thật

    @Autowired
    private DmsAutomationService dmsAutomationService;
    @Autowired
    private DmsActivationProcessor processor;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DmsConfigRepository dmsConfigRepository;
    @Autowired
    private DmsCheckinRepository dmsCheckinRepository;
    @Autowired
    private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // Mốc T0 = lúc quét kích hoạt; cắt về giây để so sánh với DATETIME của MySQL không lệch.
    private static final LocalDateTime T0 = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    // Dữ liệu test tạo ra, để @AfterEach chỉ xóa đúng phần của mình.
    private final List<Seed> seeds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Seed s : seeds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                    "%ownerId=" + s.ownerId + "%");
            List<UUID> userIds = s.userIds();
            notificationRepository.deleteAll(notificationRepository.findAll().stream()
                    .filter(n -> userIds.contains(n.getRecipient().getId()))
                    .toList());
            dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(s.configId));
            executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(s.ownerId));
            dmsConfigRepository.deleteById(s.configId);
            userRepository.deleteAllById(userIds);
        }
        seeds.clear();
    }

    // ---------- S1–S9 ----------

    @Test
    void s1_overdueCheckin_triggersDmsAndActivatesPrimaryWithEmail() {
        Seed s = seed(true, true, 14);

        dmsAutomationService.processOverdueDms(T0);

        DmsConfig config = dmsConfigRepository.findById(s.configId).orElseThrow();
        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(config.getTriggeredAt()).isEqualTo(T0);

        List<DmsCheckin> checkins = dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(s.configId);
        assertThat(checkins).singleElement()
                .satisfies(c -> {
                    assertThat(c.getCheckinType()).isEqualTo(DmsCheckinType.TIMEOUT);
                    assertThat(c.getOccurredAt()).isEqualTo(T0);
                });

        ExecutorAssignment primary = primary(s);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(primary.getActivatedAt()).isEqualTo(T0);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);   // BR-03

        Notification n = activationNotification(s, s.primaryUserId);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(n.getSentAt()).isEqualTo(T0);
        assertThat(notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(s.backupUserId)).isEmpty();
        verify(executorNotificationService).send(eq(s.primaryEmail), anyString());
        verify(executorNotificationService, never()).send(eq(s.backupEmail), anyString());

        assertThat(auditCount(s, AuditAction.DMS_TRIGGERED)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(1);
    }

    @Test
    void s2_checkinNotYetDue_changesNothing() {
        Seed s = seed(true, true, 14, T0.plusDays(5));

        dmsAutomationService.processOverdueDms(T0);
        dmsAutomationService.escalateUnresponsivePrimaries(T0);

        assertThat(dmsConfigRepository.findById(s.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(s.primaryUserId)).isEmpty();
        assertThat(totalAuditCount(s)).isZero();
        verify(executorNotificationService, never()).send(anyString(), anyString());
    }

    @Test
    void s3_silentPrimary_notEscalatedAtDay13_revokedAndBackupActivatedAtDay14() {
        Seed s = seed(true, true, 14);
        dmsAutomationService.processOverdueDms(T0);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(13));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isZero();

        LocalDateTime day14 = T0.plusDays(14);
        dmsAutomationService.escalateUnresponsivePrimaries(day14);

        ExecutorAssignment primary = primary(s);
        ExecutorAssignment backup = backup(s);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(primary.getRevokedAt()).isEqualTo(day14);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getActivatedAt()).isEqualTo(day14);
        assertThat(dmsConfigRepository.findById(s.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.TRIGGERED);

        Notification n = activationNotification(s, s.backupUserId);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isEqualTo(day14);
        verify(executorNotificationService).send(eq(s.backupEmail), anyString());

        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(2);   // Primary ở T0 + Backup ở ngày 14
    }

    @Test
    void s4_acknowledgedPrimary_isNotEscalatedBeforeSecondDeadline() {
        Seed s = seed(true, true, 14);
        dmsAutomationService.processOverdueDms(T0);

        ExecutorActivationResponse first = dmsAutomationService.acknowledgeActivation(s.primaryId, s.primaryEmail);
        assertThat(first.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(first.getAcknowledgedAt()).isNotNull();

        // Xác nhận lần 2 idempotent: giữ nguyên acknowledgedAt, không ghi audit thêm.
        ExecutorActivationResponse second = dmsAutomationService.acknowledgeActivation(s.primaryId, s.primaryEmail);
        // Lần 1 trả giá trị trong bộ nhớ, lần 2 đọc từ DATETIME(6) đã làm tròn tới micro giây.
        assertThat(second.getAcknowledgedAt()).isEqualTo(primary(s).getAcknowledgedAt())
                .isCloseTo(first.getAcknowledgedAt(), within(1, ChronoUnit.MICROS));
        assertThat(activationNotification(s, s.primaryUserId).getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACKNOWLEDGED)).isEqualTo(1);

        // B2: đã xác nhận thì không leo thang theo hạn 14 ngày kể từ kích hoạt...
        LocalDateTime acknowledgedAt = primary(s).getAcknowledgedAt();
        dmsAutomationService.escalateUnresponsivePrimaries(acknowledgedAt.plusDays(13));

        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isZero();

        // ...nhưng quá 14 ngày kể từ xác nhận mà vẫn chưa nộp hồ sơ thì leo thang sang Backup (hạn thứ hai).
        dmsAutomationService.escalateUnresponsivePrimaries(acknowledgedAt.plusDays(14));

        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isEqualTo(1);
    }

    // 401 (chưa đăng nhập) thuộc tầng security, không kiểm được ở mức service.
    @Test
    void s5_acknowledge_rejectsInactiveAssignmentAndForeignOrUnknownCaller() {
        Seed s = seed(true, true, 14);

        assertThatThrownBy(() -> dmsAutomationService.acknowledgeActivation(s.primaryId, s.primaryEmail))
                .isInstanceOf(ExecutorNotActiveException.class)
                .hasMessageContaining("not been activated");

        dmsAutomationService.processOverdueDms(T0);

        assertThatThrownBy(() -> dmsAutomationService.acknowledgeActivation(s.primaryId, s.backupEmail))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        assertThatThrownBy(() -> dmsAutomationService.acknowledgeActivation(s.primaryId, s.ownerEmail))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        assertThatThrownBy(() -> dmsAutomationService.acknowledgeActivation(UUID.randomUUID(), s.primaryEmail))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        assertThatThrownBy(() -> dmsAutomationService.acknowledgeActivation(s.backupId, s.backupEmail))
                .isInstanceOf(ExecutorNotActiveException.class);

        assertThat(primary(s).getAcknowledgedAt()).isNull();
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACKNOWLEDGED)).isZero();
    }

    @Test
    void s6_mailAlwaysFails_dmsStillTriggeredAndEscalates14DaysAfterActivatedAt() {
        Seed s = seed(true, true, 14);
        doThrow(new IllegalStateException("SMTP down")).when(executorNotificationService).send(anyString(), anyString());

        dmsAutomationService.processOverdueDms(T0);

        assertThat(dmsConfigRepository.findById(s.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        Notification n = activationNotification(s, s.primaryUserId);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getSentAt()).isNull();
        assertThat(n.getAttemptCount()).isEqualTo(1);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(13));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(activationNotification(s, s.primaryUserId).getAttemptCount()).isEqualTo(2);   // đã thử gửi lại

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(14));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(activationNotification(s, s.backupUserId).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isEqualTo(1);
    }

    // Biến thể S6 của console cũ: gửi lại thành công thì đồng hồ BR-03 tính từ sentAt (lúc Primary thật sự được báo).
    @Test
    void s6b_mailRetrySucceedsOnDay1_escalationClockStartsFromSentAt() {
        Seed s = seed(true, true, 14);
        doThrow(new IllegalStateException("SMTP down")).when(executorNotificationService).send(anyString(), anyString());
        dmsAutomationService.processOverdueDms(T0);
        reset(executorNotificationService);   // mail hoạt động lại

        LocalDateTime day1 = T0.plusDays(1);
        dmsAutomationService.escalateUnresponsivePrimaries(day1);
        Notification n = activationNotification(s, s.primaryUserId);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isEqualTo(day1);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(14));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(15));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
    }

    @Test
    void s7_waitDaysComeFromDmsConfig() {
        Seed s = seed(true, true, 3);
        dmsAutomationService.processOverdueDms(T0);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(2));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(3));
        assertThat(primary(s).getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
    }

    @Test
    void s8_noPrimary_activatesBackupDirectlyOnTrigger() {
        Seed s = seed(false, true, 14);

        dmsAutomationService.processOverdueDms(T0);

        assertThat(dmsConfigRepository.findById(s.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        ExecutorAssignment backup = backup(s);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getActivatedAt()).isEqualTo(T0);
        assertThat(activationNotification(s, s.backupUserId).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.DMS_TRIGGERED_NO_EXECUTOR)).isZero();

        // Leo thang về sau không đụng tới Backup đã ACTIVE.
        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(30));
        assertThat(backup(s).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(1);
    }

    @Test
    void s9_noExecutor_triggersDmsAndAuditsWarningWithoutError() {
        Seed s = seed(false, false, 14);

        dmsAutomationService.processOverdueDms(T0);
        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(14));

        assertThat(dmsConfigRepository.findById(s.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(executorAssignmentRepository.findAllByOwnerId(s.ownerId)).isEmpty();
        assertThat(auditCount(s, AuditAction.DMS_TRIGGERED)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.DMS_TRIGGERED_NO_EXECUTOR)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isZero();
        verify(executorNotificationService, never()).send(anyString(), anyString());
    }

    // ---------- Bổ sung ngoài S1–S9 ----------

    @Test
    void noBackup_silentPrimaryStaysActiveAfter14Days() {
        Seed s = seed(true, false, 14);
        dmsAutomationService.processOverdueDms(T0);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(14));
        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(30));

        ExecutorAssignment primary = primary(s);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(primary.getRevokedAt()).isNull();
        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isZero();
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(1);
    }

    @Test
    void scanningTwiceInARow_isIdempotent() {
        Seed s = seed(true, true, 14);

        dmsAutomationService.processOverdueDms(T0);
        dmsAutomationService.processOverdueDms(T0);
        assertThat(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(s.configId)).hasSize(1);
        assertThat(notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(s.primaryUserId)).hasSize(1);
        assertThat(auditCount(s, AuditAction.DMS_TRIGGERED)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(1);
        assertThat(executorRoleCount(s.primaryUserId)).isEqualTo(1);   // C2/T7
        assertThat(executorRoleCount(s.backupUserId)).isZero();

        LocalDateTime day14 = T0.plusDays(14);
        dmsAutomationService.escalateUnresponsivePrimaries(day14);
        long primaryVersion = primary(s).getVersion();
        long backupVersion = backup(s).getVersion();
        dmsAutomationService.escalateUnresponsivePrimaries(day14);

        assertThat(primary(s).getVersion()).isEqualTo(primaryVersion);
        assertThat(backup(s).getVersion()).isEqualTo(backupVersion);
        assertThat(notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(s.backupUserId)).hasSize(1);
        assertThat(auditCount(s, AuditAction.EXECUTOR_REVOKE)).isEqualTo(1);
        assertThat(auditCount(s, AuditAction.EXECUTOR_ACTIVATED)).isEqualTo(2);
        verify(executorNotificationService, times(1)).send(eq(s.backupEmail), anyString());
        assertThat(executorRoleCount(s.backupUserId)).isEqualTo(1);
    }

    // ---------- B1: acknowledge và escalate chạy đua trên cùng một Primary ----------

    @Test
    void acknowledgeRacingEscalation_neverLeavesInconsistentExecutors() throws Exception {
        int rounds = 30;
        int ackWins = 0;
        int escalateWins = 0;
        int conflicts = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                Seed s = seed(true, true, 14);
                dmsAutomationService.processOverdueDms(T0);
                LocalDateTime day14 = T0.plusDays(14);
                // acknowledge ít truy vấn hơn nên gần như luôn tới flush trước; lệch xuất phát 0–18 ms theo vòng
                // để escalate cũng có lúc đi trước. Không ảnh hưởng assert vì test không assert bên nào thắng.
                long ackHeadStartNanos = TimeUnit.MILLISECONDS.toNanos(2L * (round % 10));

                List<Throwable> results = runConcurrently(pool, List.of(
                        () -> {
                            spin(ackHeadStartNanos);
                            return dmsAutomationService.acknowledgeActivation(s.primaryId, s.primaryEmail);
                        },
                        () -> {
                            processor.escalateIfPrimaryUnresponsive(s.configId, day14);
                            return null;
                        }));

                // Bên thua chỉ được thua theo cách đã dự liệu, không có lỗi lạ.
                Throwable ackError = results.get(0);
                Throwable escalateError = results.get(1);
                if (ackError != null) {
                    assertThat(ackError).as("round %d acknowledge", round).isInstanceOfAny(
                            ObjectOptimisticLockingFailureException.class, ExecutorNotActiveException.class);
                }
                if (escalateError != null) {
                    assertThat(escalateError).as("round %d escalate", round)
                            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
                }

                ExecutorAssignment primary = primary(s);
                ExecutorAssignment backup = backup(s);
                if (primary.getStatus() == ExecutorStatus.REVOKED) {
                    assertThat(primary.getAcknowledgedAt()).as("round %d: Primary REVOKED nhưng đã acknowledge", round)
                            .isNull();
                }
                assertThat(primary.getStatus() == ExecutorStatus.ACTIVE && backup.getStatus() == ExecutorStatus.ACTIVE)
                        .as("round %d: Primary và Backup cùng ACTIVE", round)
                        .isFalse();

                if (primary.getStatus() == ExecutorStatus.REVOKED) {
                    escalateWins++;
                } else if (primary.getAcknowledgedAt() != null) {
                    ackWins++;
                }
                if (ackError instanceof ObjectOptimisticLockingFailureException
                        || escalateError instanceof ObjectOptimisticLockingFailureException) {
                    conflicts++;
                }
                cleanUp();
            }
        } finally {
            pool.shutdownNow();
        }
        log.info("B1 race: acknowledge won={}, escalate won={}, version conflicts={} / {} rounds",
                ackWins, escalateWins, conflicts, rounds);
    }

    private static void spin(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    // ---------- helpers ----------

    private record Seed(UUID ownerId, String ownerEmail, UUID configId,
                        UUID primaryId, UUID primaryUserId, String primaryEmail,
                        UUID backupId, UUID backupUserId, String backupEmail) {
        List<UUID> userIds() {
            List<UUID> ids = new ArrayList<>();
            ids.add(ownerId);
            if (primaryUserId != null) ids.add(primaryUserId);
            if (backupUserId != null) ids.add(backupUserId);
            return ids;
        }
    }

    private Seed seed(boolean withPrimary, boolean withBackup, int waitDays) {
        return seed(withPrimary, withBackup, waitDays, T0.minusMinutes(1));
    }

    private Seed seed(boolean withPrimary, boolean withBackup, int waitDays, LocalDateTime nextCheckinDueAt) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = user("owner-" + tag);
        DmsConfig config = dmsConfigRepository.save(DmsConfig.builder()
                .owner(owner)
                .backupActivationWaitDays(waitDays)
                .nextCheckinDueAt(nextCheckinDueAt)
                // Đã gửi được 1 lần nhắc: trigger không bị hoãn theo Q27 (B3).
                .remindersSent(1)
                .build());

        User primaryUser = withPrimary ? user("primary-" + tag) : null;
        ExecutorAssignment primary = withPrimary ? assignment(owner, primaryUser, ExecutorType.PRIMARY) : null;
        User backupUser = withBackup ? user("backup-" + tag) : null;
        ExecutorAssignment backup = withBackup ? assignment(owner, backupUser, ExecutorType.BACKUP) : null;

        Seed s = new Seed(owner.getId(), owner.getEmail(), config.getId(),
                primary == null ? null : primary.getId(), primaryUser == null ? null : primaryUser.getId(),
                primaryUser == null ? null : primaryUser.getEmail(),
                backup == null ? null : backup.getId(), backupUser == null ? null : backupUser.getId(),
                backupUser == null ? null : backupUser.getEmail());
        seeds.add(s);
        return s;
    }

    private User user(String name) {
        return userRepository.save(User.builder()
                .email(name + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private ExecutorAssignment assignment(User owner, User executor, ExecutorType type) {
        return executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executor)
                .executorType(type)
                .build());
    }

    private ExecutorAssignment primary(Seed s) {
        return executorAssignmentRepository.findById(s.primaryId).orElseThrow();
    }

    private ExecutorAssignment backup(Seed s) {
        return executorAssignmentRepository.findById(s.backupId).orElseThrow();
    }

    private Notification activationNotification(Seed s, UUID recipientId) {
        return notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(recipientId,
                        NotificationType.EXECUTOR_ACTIVATED, DmsActivationProcessor.ENTITY_DMS_CONFIG, s.configId)
                .orElseThrow();
    }

    // Mọi audit FR-07 đều có "ownerId=..." trong metadata.detail, dùng nó để chỉ đếm audit của Owner trong test.
    private int auditCount(Seed s, AuditAction action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ?"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                Integer.class, action.name(), "%ownerId=" + s.ownerId + "%");
    }

    private long executorRoleCount(UUID userId) {
        return transactionTemplate.execute(tx -> userRepository.findById(userId).orElseThrow().getRoles().stream()
                .filter(r -> "EXECUTOR".equals(r.getCode()))
                .count());
    }

    private int totalAuditCount(Seed s) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                Integer.class, "%ownerId=" + s.ownerId + "%");
    }

    /** Chạy các task cùng lúc; phần tử thứ i = null nếu task i thành công, ngược lại là exception. */
    private List<Throwable> runConcurrently(ExecutorService pool, List<Callable<?>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.call();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        ready.await();
        start.countDown();
        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        return results;
    }
}
