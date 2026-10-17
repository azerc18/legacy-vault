package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FR-03 chạy trên MySQL thật: lưu cấu hình DMS, nhắc check-in, check-in, và chỗ nối với FR-07 (trigger).
 * upsertConfig/checkIn lấy giờ thật (LocalDateTime.now()); các mốc tương lai được truyền tường minh vào
 * reminder service và processOverdueDms, hoặc dời lịch về quá khứ qua repository. Không sleep.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class DmsConfigIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DmsConfigIntegrationTest.class);

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;                                     // không gửi mail thật
    @MockitoBean
    private ExecutorNotificationService executorNotificationService;   // không gửi mail thật

    @Autowired
    private DmsConfigService dmsConfigService;
    @Autowired
    private DmsReminderService dmsReminderService;
    @Autowired
    private DmsAutomationService dmsAutomationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private VaultRepository vaultRepository;
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

    private static final int INTERVAL_DAYS = 30;
    private static final int GRACE_DAYS = 7;
    private static final int REMINDERS = 3;

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
            dmsConfigRepository.findByOwnerId(s.ownerId).ifPresent(c -> {
                dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(c.getId()));
                dmsConfigRepository.delete(c);
            });
            executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(s.ownerId));
            vaultRepository.deleteAll(vaultRepository.findAll().stream()
                    .filter(v -> v.getOwner().getId().equals(s.ownerId))
                    .toList());
            userRepository.deleteAllById(userIds);
        }
        seeds.clear();
    }

    // ---------- 1. Lưu cấu hình lần đầu ----------

    @Test
    void firstUpsert_activatesDmsAndSchedulesFromNow() {
        Seed s = seed(true, false);
        LocalDateTime before = LocalDateTime.now();

        DmsConfigService.UpsertResult result = dmsConfigService.upsertConfig(s.ownerId, request());

        LocalDateTime after = LocalDateTime.now();
        assertThat(result.created()).isTrue();
        DmsConfig config = config(s);
        assertThat(config.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(config.getNotificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(config.getBackupActivationWaitDays()).isEqualTo(14);
        assertThat(config.getRemindersSent()).isZero();
        // DATETIME(6) làm tròn tới micro giây nên nới biên 1 ms.
        assertThat(config.getLastCheckinAt()).isBetween(before.minusNanos(1_000_000), after.plusNanos(1_000_000));
        LocalDateTime start = config.getLastCheckinAt();
        assertThat(config.getNextCheckinDueAt()).isEqualTo(start.plusDays(INTERVAL_DAYS + GRACE_DAYS));
        assertThat(config.getNextReminderAt()).isEqualTo(start.plusDays(INTERVAL_DAYS));
        assertThat(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(config.getId())).isEmpty();
    }

    @Test
    void firstUpsert_ownerWithoutVault_returns409AndCreatesNothing() {
        Seed s = seed(false, false);

        assertThatThrownBy(() -> dmsConfigService.upsertConfig(s.ownerId, request()))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dmsConfigRepository.findByOwnerId(s.ownerId)).isEmpty();
    }

    // ---------- 2. Nhắc nhở với DB thật ----------

    @Test
    void reminders_sentOncePerDueTime_thenStop() {
        Seed s = seed(true, false);
        dmsConfigService.upsertConfig(s.ownerId, request());
        LocalDateTime start = config(s).getLastCheckinAt();
        LocalDateTime due = start.plusDays(INTERVAL_DAYS);
        LocalDateTime triggerAt = due.plusDays(GRACE_DAYS);
        List<LocalDateTime> reminderTimes = List.of(due, due.plusHours(56), due.plusHours(112));

        // Ngay trước lần nhắc đầu: chưa gửi gì.
        dmsReminderService.sendDueReminders(due.minusSeconds(1));
        assertThat(config(s).getRemindersSent()).isZero();

        for (int i = 0; i < REMINDERS; i++) {
            dmsReminderService.sendDueReminders(reminderTimes.get(i));

            DmsConfig config = config(s);
            assertThat(config.getRemindersSent()).as("sau lần nhắc %d", i + 1).isEqualTo(i + 1);
            assertThat(config.getNextReminderAt()).as("lần nhắc kế sau lần %d", i + 1)
                    .isEqualTo(i + 1 < REMINDERS ? reminderTimes.get(i + 1) : null);
            assertThat(config.getNextCheckinDueAt()).isEqualTo(triggerAt);   // nhắc không lùi hạn chót
            verify(emailSender).sendDmsCheckinReminder(s.ownerEmail, due, triggerAt, i + 1, REMINDERS);
            assertThat(reminderCheckins(s)).hasSize(i + 1);
        }

        // Quét lại tại lần cuối và gần hạn chót: không gửi trùng.
        dmsReminderService.sendDueReminders(reminderTimes.get(REMINDERS - 1));
        dmsReminderService.sendDueReminders(triggerAt.minusMinutes(1));

        assertThat(config(s).getRemindersSent()).isEqualTo(REMINDERS);
        assertThat(config(s).getNextReminderAt()).isNull();
        assertThat(reminderCheckins(s)).hasSize(REMINDERS)
                .allSatisfy(c -> assertThat(c.getChannel()).isEqualTo(NotificationChannel.EMAIL));
        verify(emailSender, times(REMINDERS)).sendDmsCheckinReminder(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void reminderMailFails_thenRetriedNextScan_recordsExactlyOneReminder() {
        Seed s = seed(true, false);
        dmsConfigService.upsertConfig(s.ownerId, request());
        LocalDateTime due = config(s).getLastCheckinAt().plusDays(INTERVAL_DAYS);
        LocalDateTime triggerAt = due.plusDays(GRACE_DAYS);
        doThrow(new MailSendException("SMTP down")).doNothing().when(emailSender)
                .sendDmsCheckinReminder(anyString(), any(), any(), anyInt(), anyInt());

        // Lượt 1: gửi lỗi -> trả lại lần nhắc, không ghi REMINDER_SENT.
        dmsReminderService.sendDueReminders(due);

        assertThat(config(s).getRemindersSent()).isZero();
        assertThat(config(s).getNextReminderAt()).isEqualTo(due);
        assertThat(reminderCheckins(s)).isEmpty();

        // Lượt 2 (15 phút sau): gửi lại đúng lần nhắc 1 và thành công.
        dmsReminderService.sendDueReminders(due.plusMinutes(15));

        DmsConfig config = config(s);
        assertThat(config.getRemindersSent()).isEqualTo(1);
        assertThat(config.getNextReminderAt()).isEqualTo(due.plusHours(56));
        assertThat(reminderCheckins(s)).hasSize(1);
        verify(emailSender, times(2)).sendDmsCheckinReminder(s.ownerEmail, due, triggerAt, 1, REMINDERS);
    }

    // ---------- 3. Check-in cứu DMS ----------

    @Test
    void checkInAfterReminder_resetsCounterAndOldDeadlineNoLongerTriggers() {
        Seed s = seed(true, true);
        dmsConfigService.upsertConfig(s.ownerId, request());
        // Dời chu kỳ về 31 ngày trước: đã qua hạn check-in 1 ngày, còn 6 ngày tới hạn chót.
        backdateCycle(s, 31);
        LocalDateTime oldDeadline = config(s).getNextCheckinDueAt();

        dmsReminderService.sendDueReminders(LocalDateTime.now());
        assertThat(config(s).getRemindersSent()).isEqualTo(1);

        dmsConfigService.checkIn(s.ownerId);

        DmsConfig config = config(s);
        assertThat(config.getRemindersSent()).isZero();
        assertThat(config.getNextCheckinDueAt()).isAfter(oldDeadline.plusDays(INTERVAL_DAYS - 1));
        assertThat(config.getNextReminderAt()).isEqualTo(config.getLastCheckinAt().plusDays(INTERVAL_DAYS));
        assertThat(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(config.getId()))
                .extracting(DmsCheckin::getCheckinType)
                .containsExactly(DmsCheckinType.OWNER_RESPONDED, DmsCheckinType.REMINDER_SENT);

        // FR-07 chạy tại hạn chót CŨ: không còn quá hạn.
        dmsAutomationService.processOverdueDms(oldDeadline.plusMinutes(1));

        assertThat(config(s).getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(executorAssignmentRepository.findAllByOwnerId(s.ownerId))
                .allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(ExecutorStatus.INACTIVE));
        verify(executorNotificationService, never()).send(anyString(), anyString());
    }

    // ---------- 4. Nối FR-03 với FR-07 ----------

    @Test
    void noCheckIn_triggersAtNewDeadline_thenCheckInRejected() {
        Seed s = seed(true, true);
        dmsConfigService.upsertConfig(s.ownerId, request());
        LocalDateTime deadline = config(s).getNextCheckinDueAt();

        // Ngay trước hạn chót: chưa kích hoạt.
        dmsAutomationService.processOverdueDms(deadline.minusSeconds(1));
        assertThat(config(s).getStatus()).isEqualTo(DmsStatus.ACTIVE);

        dmsAutomationService.processOverdueDms(deadline.plusMinutes(1));

        assertThat(config(s).getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(assignment(s.primaryId).getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(assignment(s.backupId).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        verify(executorNotificationService).send(eq(s.primaryEmail), anyString());

        assertThatThrownBy(() -> dmsConfigService.checkIn(s.ownerId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(config(s).getStatus()).isEqualTo(DmsStatus.TRIGGERED);
    }

    // ---------- 5. Race check-in và trigger ----------

    @Test
    void checkInRacingTrigger_neverBothSucceed() throws Exception {
        int rounds = 30;
        int checkInWins = 0;
        int triggerWins = 0;
        int optimisticConflicts = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                Seed s = seed(true, true);
                dmsConfigService.upsertConfig(s.ownerId, request());
                DmsConfig overdue = config(s);
                overdue.setNextCheckinDueAt(LocalDateTime.now().minusMinutes(1));
                dmsConfigRepository.saveAndFlush(overdue);
                LocalDateTime now = LocalDateTime.now();
                // check-in ít truy vấn hơn trigger nên gần như luôn tới flush trước; lệch xuất phát 0–18 ms theo vòng
                // để trigger cũng có lúc đi trước. Không ảnh hưởng assert vì test không assert bên nào thắng.
                long checkInHeadStartNanos = TimeUnit.MILLISECONDS.toNanos(2L * (round % 10));

                List<Throwable> results = runConcurrently(pool, List.of(
                        () -> {
                            spin(checkInHeadStartNanos);
                            return dmsConfigService.checkIn(s.ownerId);
                        },
                        () -> {
                            dmsAutomationService.processOverdueDms(now);
                            return null;
                        }));
                Throwable checkInError = results.get(0);

                // processOverdueDms tự bắt lỗi từng Owner; không được có gì lọt ra ngoài.
                assertThat(results.get(1)).as("round %d trigger", round).isNull();
                if (checkInError != null) {
                    assertThat(checkInError).as("round %d check-in", round)
                            .satisfiesAnyOf(
                                    e -> assertThat(e).isInstanceOf(ObjectOptimisticLockingFailureException.class),
                                    e -> assertThat(e).isInstanceOf(DmsException.class)
                                            .extracting(x -> ((DmsException) x).getStatus())
                                            .isEqualTo(HttpStatus.CONFLICT));
                }

                DmsConfig config = config(s);
                ExecutorAssignment primary = assignment(s.primaryId);
                ExecutorAssignment backup = assignment(s.backupId);
                boolean checkInSucceeded = checkInError == null;
                boolean triggered = config.getStatus() == DmsStatus.TRIGGERED;
                assertThat(checkInSucceeded && triggered).as("round %d: check-in thành công nhưng DMS TRIGGERED", round)
                        .isFalse();
                assertThat(checkInSucceeded || triggered).as("round %d: cả hai cùng thất bại", round).isTrue();
                if (checkInSucceeded) {
                    assertThat(config.getStatus()).isEqualTo(DmsStatus.ACTIVE);
                    assertThat(config.getNextCheckinDueAt()).as("round %d hạn chót", round).isAfter(now);
                    assertThat(primary.getStatus()).isNotEqualTo(ExecutorStatus.ACTIVE);
                    assertThat(backup.getStatus()).isNotEqualTo(ExecutorStatus.ACTIVE);
                    checkInWins++;
                } else {
                    triggerWins++;
                    if (checkInError instanceof ObjectOptimisticLockingFailureException) {
                        optimisticConflicts++;
                    }
                }
                cleanUp();
            }
        } finally {
            pool.shutdownNow();
        }
        log.info("FR-03 race check-in vs trigger: check-in won={}, trigger won={} (check-in lost by version conflict={}) / {} rounds",
                checkInWins, triggerWins, optimisticConflicts, rounds);
    }

    // ---------- 6. Tạo cấu hình đồng thời ----------

    @Test
    void concurrentFirstUpsert_createsExactlyOneConfig() throws Exception {
        int rounds = 20;
        int conflictRounds = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                Seed s = seed(true, false);
                List<DmsConfigService.UpsertResult> successes = new ArrayList<>();

                List<Throwable> results = runConcurrently(pool, List.of(
                        () -> dmsConfigService.upsertConfig(s.ownerId, request()),
                        () -> dmsConfigService.upsertConfig(s.ownerId, request())));

                int failures = 0;
                for (Throwable t : results) {
                    if (t != null) {
                        failures++;
                        assertThat(t).as("round %d", round).isInstanceOf(DmsException.class)
                                .extracting(x -> ((DmsException) x).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    }
                }
                assertThat(failures).as("round %d: không được cả hai cùng thất bại", round).isLessThan(2);
                if (failures == 1) {
                    conflictRounds++;
                }

                long rows = dmsConfigRepository.findAll().stream()
                        .filter(c -> c.getOwner().getId().equals(s.ownerId)).count();
                assertThat(rows).as("round %d: số dòng DmsConfig", round).isEqualTo(1);
                cleanUp();
            }
        } finally {
            pool.shutdownNow();
        }
        log.info("FR-03 concurrent first upsert: rounds with 409={} / {} (others: second call saw the row and updated it)",
                conflictRounds, rounds);
    }

    // ---------- 7. Hàm chặn dùng chung (O1) ----------

    @Test
    void assertHandoverNotStarted_outsideTransaction_throwsIllegalTransactionState() {
        Seed s = seed(true, false);
        dmsConfigService.upsertConfig(s.ownerId, request());

        // MANDATORY: gọi ngoài transaction thì khóa FOR UPDATE vô nghĩa, phải bị từ chối ngay.
        assertThatThrownBy(() -> dmsConfigService.assertHandoverNotStarted(s.ownerId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void assertHandoverNotStarted_insideTransaction_whenTriggered_returns409() {
        Seed s = seed(true, false);
        dmsConfigService.upsertConfig(s.ownerId, request());
        DmsConfig config = config(s);
        config.setStatus(DmsStatus.TRIGGERED);
        dmsConfigRepository.saveAndFlush(config);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                tx -> dmsConfigService.assertHandoverNotStarted(s.ownerId)))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(config(s).getStatus()).isEqualTo(DmsStatus.TRIGGERED);
    }

    // ---------- helpers ----------

    private record Seed(UUID ownerId, String ownerEmail, UUID primaryId, UUID primaryUserId, String primaryEmail,
                        UUID backupId, UUID backupUserId) {
        List<UUID> userIds() {
            List<UUID> ids = new ArrayList<>();
            ids.add(ownerId);
            if (primaryUserId != null) ids.add(primaryUserId);
            if (backupUserId != null) ids.add(backupUserId);
            return ids;
        }
    }

    private Seed seed(boolean withVault, boolean withExecutors) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = user("owner-" + tag);
        if (withVault) {
            vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        }
        Seed s;
        if (withExecutors) {
            User primaryUser = user("primary-" + tag);
            User backupUser = user("backup-" + tag);
            ExecutorAssignment primary = assignment(owner, primaryUser, ExecutorType.PRIMARY);
            ExecutorAssignment backup = assignment(owner, backupUser, ExecutorType.BACKUP);
            s = new Seed(owner.getId(), owner.getEmail(), primary.getId(), primaryUser.getId(), primaryUser.getEmail(),
                    backup.getId(), backupUser.getId());
        } else {
            s = new Seed(owner.getId(), owner.getEmail(), null, null, null, null, null);
        }
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

    private static DmsConfigRequest request() {
        DmsConfigRequest request = new DmsConfigRequest();
        request.setCheckinIntervalDays(INTERVAL_DAYS);
        request.setGracePeriodDays(GRACE_DAYS);
        request.setReminderCount(REMINDERS);
        return request;
    }

    private DmsConfig config(Seed s) {
        return dmsConfigRepository.findByOwnerId(s.ownerId).orElseThrow();
    }

    private ExecutorAssignment assignment(UUID id) {
        return executorAssignmentRepository.findById(id).orElseThrow();
    }

    private List<DmsCheckin> reminderCheckins(Seed s) {
        return dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(config(s).getId()).stream()
                .filter(c -> c.getCheckinType() == DmsCheckinType.REMINDER_SENT)
                .toList();
    }

    /** Dời toàn bộ chu kỳ hiện tại về quá khứ như thể cấu hình được lưu daysAgo ngày trước. */
    private void backdateCycle(Seed s, int daysAgo) {
        DmsConfig config = config(s);
        LocalDateTime start = LocalDateTime.now().minusDays(daysAgo).truncatedTo(ChronoUnit.MICROS);
        config.setLastCheckinAt(start);
        config.setNextCheckinDueAt(start.plusDays(INTERVAL_DAYS + GRACE_DAYS));
        config.setNextReminderAt(start.plusDays(INTERVAL_DAYS));
        dmsConfigRepository.saveAndFlush(config);
    }

    private static void spin(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
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
