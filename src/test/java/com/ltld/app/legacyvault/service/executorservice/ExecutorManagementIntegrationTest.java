package com.ltld.app.legacyvault.service.executorservice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationServiceImpl;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * FR-06 chạy trên MySQL thật: chỉ định / thay / thu hồi Executor, nối với FR-07 (trigger + BR-03),
 * role EXECUTOR qua nhiều Owner, và các cuộc đua với trigger / request song song.
 * Mail FR-06 gửi @Async sau AFTER_COMMIT: kiểm "đã gửi" bằng timeout(...), "không gửi" bằng after(...).
 * Mọi mốc thời gian của FR-07 truyền tường minh qua tham số now, không sleep; scheduler tắt.
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
class ExecutorManagementIntegrationTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ExecutorManagementIntegrationTest.class);

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    // Mail đã gửi chạy trên thread @Async: chờ tối đa MAIL_WAIT_MS; kiểm "không gửi" chờ NO_MAIL_WAIT_MS.
    private static final long MAIL_WAIT_MS = 5_000;
    private static final long NO_MAIL_WAIT_MS = 300;

    // Mốc T0 = lúc quét kích hoạt; cắt về giây để so sánh với DATETIME của MySQL không lệch.
    private static final LocalDateTime T0 = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @MockitoBean
    private EmailSender emailSender;                                   // mail FR-06
    @MockitoBean
    private ExecutorNotificationService executorNotificationService;   // mail kích hoạt FR-07

    @Autowired private ExecutorManagementService executorManagementService;
    @Autowired private DmsAutomationService dmsAutomationService;
    @Autowired private UserRepository userRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private DmsCheckinRepository dmsCheckinRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    // Dữ liệu test tạo ra, để @AfterEach chỉ xóa đúng phần của mình.
    private final List<UUID> ownerIds = new ArrayList<>();
    private final List<UUID> configIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    // processOverdueDms nuốt lỗi của từng Owner (chỉ log error) -> bắt log để biết trigger thất bại vì sao.
    private final ListAppender<ILoggingEvent> dmsLog = new ListAppender<>();
    private Logger dmsLogger;

    @BeforeEach
    void captureDmsLog() {
        dmsLogger = (Logger) LoggerFactory.getLogger(DmsAutomationServiceImpl.class);
        dmsLog.list.clear();
        dmsLog.start();
        dmsLogger.addAppender(dmsLog);
    }

    @AfterEach
    void cleanUp() {
        dmsLogger.detachAppender(dmsLog);
        dmsLog.stop();
        deleteSeededData();
    }

    private void deleteSeededData() {
        for (UUID ownerId : ownerIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                    "%ownerId=" + ownerId + "%");
        }
        notificationRepository.deleteAll(notificationRepository.findAll().stream()
                .filter(n -> userIds.contains(n.getRecipient().getId()))
                .toList());
        for (UUID configId : configIds) {
            dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(configId));
        }
        for (UUID ownerId : ownerIds) {
            executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(ownerId));
        }
        dmsConfigRepository.deleteAllById(configIds);
        userRepository.deleteAllById(userIds);   // Hibernate xóa luôn dòng user_roles của user
        ownerIds.clear();
        configIds.clear();
        userIds.clear();
    }

    // ---------- 1. Gán Primary + Backup ----------

    @Test
    void assignPrimaryAndBackup_createsInactiveRows_grantsRole_listsThem_andMailsBoth() {
        Owner o = owner(T0.plusDays(5));
        User a = user("a");
        User b = user("b");

        ExecutorAssignmentResponse p = assign(o, ExecutorType.PRIMARY, a, false);
        ExecutorAssignmentResponse bk = assign(o, ExecutorType.BACKUP, b, false);

        assertThat(p.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(bk.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(executorAssignmentRepository.findAllByOwnerId(o.id)).hasSize(2)
                .allSatisfy(x -> {
                    assertThat(x.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
                    assertThat(x.getAssignedAt()).isNotNull();
                    assertThat(x.getActivatedAt()).isNull();
                });
        assertThat(hasExecutorRole(a.getId())).isTrue();
        assertThat(hasExecutorRole(b.getId())).isTrue();

        List<ExecutorAssignmentResponse> list = executorManagementService.listExecutors(o.id);
        assertThat(list).extracting(ExecutorAssignmentResponse::getExecutorType)
                .containsExactly(ExecutorType.PRIMARY, ExecutorType.BACKUP);
        assertThat(list).extracting(ExecutorAssignmentResponse::getEmail)
                .containsExactly(a.getEmail(), b.getEmail());
        assertThat(list).extracting(ExecutorAssignmentResponse::getAssignmentId)
                .containsExactly(p.getAssignmentId(), bk.getAssignmentId());

        verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                a.getEmail(), o.fullName, o.email, ExecutorType.PRIMARY);
        verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                b.getEmail(), o.fullName, o.email, ExecutorType.BACKUP);
        assertThat(auditCount(o, AuditAction.EXECUTOR_ASSIGNED)).isEqualTo(2);
    }

    // ---------- 2. FR-06 -> FR-07 ----------

    @Test
    void assignedExecutors_areUsedByDmsTriggerAndBr03Escalation() {
        Owner o = owner(T0.plusDays(5));
        User a = user("a");
        User b = user("b");
        UUID primaryId = assign(o, ExecutorType.PRIMARY, a, false).getAssignmentId();
        UUID backupId = assign(o, ExecutorType.BACKUP, b, false).getAssignmentId();

        DmsConfig config = dmsConfigRepository.findById(o.configId).orElseThrow();
        config.setNextCheckinDueAt(T0.minusMinutes(1));
        dmsConfigRepository.save(config);

        dmsAutomationService.processOverdueDms(T0);

        assertThat(dmsConfigRepository.findById(o.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        ExecutorAssignment primary = executorAssignmentRepository.findById(primaryId).orElseThrow();
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(primary.getActivatedAt()).isEqualTo(T0);
        assertThat(executorAssignmentRepository.findById(backupId).orElseThrow().getStatus())
                .isEqualTo(ExecutorStatus.INACTIVE);
        verify(executorNotificationService).send(eq(a.getEmail()), anyString());

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(13));
        assertThat(executorAssignmentRepository.findById(backupId).orElseThrow().getStatus())
                .isEqualTo(ExecutorStatus.INACTIVE);

        dmsAutomationService.escalateUnresponsivePrimaries(T0.plusDays(14));
        assertThat(executorAssignmentRepository.findById(primaryId).orElseThrow().getStatus())
                .isEqualTo(ExecutorStatus.REVOKED);
        ExecutorAssignment backup = executorAssignmentRepository.findById(backupId).orElseThrow();
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getExecutorUser().getId()).isEqualTo(b.getId());
        verify(executorNotificationService).send(eq(b.getEmail()), anyString());
    }

    // ---------- 3. Thu hồi rồi gán lại ----------

    @Test
    void revokeThenReassign_reusesSameRowAndClearsTimestamps_auditInOrder() {
        Owner o = owner(T0.plusDays(5));
        User c = user("c");
        User d = user("d");
        UUID rowId = assign(o, ExecutorType.PRIMARY, c, false).getAssignmentId();

        executorManagementService.revokeExecutor(o.id, ExecutorType.PRIMARY);
        ExecutorAssignment revoked = executorAssignmentRepository.findById(rowId).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isNotNull();
        // Giả lập dòng từng được FR-07 kích hoạt trước khi bị thu hồi, để kiểm mốc thời gian được xóa hết.
        revoked.setActivatedAt(T0.minusDays(3));
        revoked.setAcknowledgedAt(T0.minusDays(2));
        executorAssignmentRepository.save(revoked);

        ExecutorAssignmentResponse again = assign(o, ExecutorType.PRIMARY, d, false);

        assertThat(again.getAssignmentId()).isEqualTo(rowId);
        assertThat(executorAssignmentRepository.findAllByOwnerId(o.id)).hasSize(1);
        ExecutorAssignment row = executorAssignmentRepository.findById(rowId).orElseThrow();
        assertThat(row.getExecutorUser().getId()).isEqualTo(d.getId());
        assertThat(row.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(row.getActivatedAt()).isNull();
        assertThat(row.getAcknowledgedAt()).isNull();
        assertThat(row.getRevokedAt()).isNull();

        List<String> actions = jdbcTemplate.queryForList(
                "SELECT action FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?"
                        + " ORDER BY created_at", String.class, "%ownerId=" + o.id + "%");
        assertThat(actions).containsExactly(AuditAction.EXECUTOR_ASSIGNED.name(),
                AuditAction.EXECUTOR_REVOKE.name(), AuditAction.EXECUTOR_ASSIGNED.name());
        String revokeDetail = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) FROM audit_logs WHERE action = ?"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                String.class, AuditAction.EXECUTOR_REVOKE.name(), "%ownerId=" + o.id + "%");
        assertThat(revokeDetail).contains("reason=OWNER", "executorUserId=" + c.getId());

        verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorRevokedNotice(
                c.getEmail(), o.fullName, o.email, ExecutorType.PRIMARY);
        verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                d.getEmail(), o.fullName, o.email, ExecutorType.PRIMARY);
    }

    // ---------- 4. Role qua nhiều Owner ----------

    @Test
    void executorRole_isRemovedOnlyWhenNoActiveAssignmentLeftAcrossOwners() {
        Owner ownerA = owner(null);
        Owner ownerB = owner(null);
        User x = user("x");
        assign(ownerA, ExecutorType.PRIMARY, x, false);
        assign(ownerB, ExecutorType.BACKUP, x, false);
        assertThat(hasExecutorRole(x.getId())).isTrue();

        executorManagementService.revokeExecutor(ownerA.id, ExecutorType.PRIMARY);
        assertThat(hasExecutorRole(x.getId())).as("vẫn là Executor của Owner B").isTrue();

        executorManagementService.revokeExecutor(ownerB.id, ExecutorType.BACKUP);
        assertThat(hasExecutorRole(x.getId())).as("không còn assignment nào chưa REVOKED").isFalse();

        verify(emailSender, timeout(MAIL_WAIT_MS).times(2)).sendExecutorRevokedNotice(
                eq(x.getEmail()), anyString(), anyString(), any());
    }

    // ---------- 4b. m1 (T2) Race: hai Owner cùng thao tác trên một người ----------

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void ownerARevokesWhileOwnerBAssignsSameUser_userKeepsExecutorRole_noDeadlock() throws Exception {
        int rounds = 20;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                Owner ownerA = owner(null);
                Owner ownerB = owner(null);
                User x = user("x" + round);
                assign(ownerA, ExecutorType.PRIMARY, x, false);

                // Vòng chẵn cho thu hồi xuất phát trễ, vòng lẻ cho chỉ định xuất phát trễ (0–4 ms), để cả hai thứ tự đều xảy ra.
                boolean delayRevoke = round % 2 == 0;
                long headStartNanos = TimeUnit.MILLISECONDS.toNanos(round / 2 % 5);
                List<Throwable> results = runConcurrently(pool, List.of(
                        () -> {
                            if (delayRevoke) spin(headStartNanos);
                            return executorManagementService.revokeExecutor(ownerA.id, ExecutorType.PRIMARY);
                        },
                        () -> {
                            if (!delayRevoke) spin(headStartNanos);
                            return assign(ownerB, ExecutorType.PRIMARY, x, false);
                        }));

                assertThat(results).as("vòng %d: cả hai thao tác thành công, không deadlock/lỗi khóa", round)
                        .containsOnlyNulls();
                boolean stillAssigned = executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                        x.getId(), ExecutorStatus.REVOKED, UUID.randomUUID());
                assertThat(stillAssigned).as("vòng %d: X vẫn là Executor của Owner B", round).isTrue();
                assertThat(hasExecutorRole(x.getId()))
                        .as("vòng %d: X còn assignment chưa REVOKED thì phải còn role EXECUTOR", round).isTrue();

                deleteSeededData();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- 5. Race: PUT thay Primary vs trigger của FR-07 ----------

    @Test
    void replacePrimaryRacingDmsTrigger_noGhostAudit_only409_singleActiveExecutor() throws Exception {
        int rounds = 30;
        int triggerFirst = 0;        // trigger commit trước -> PUT 409
        int putFirstTriggerLater = 0; // PUT commit trước, trigger chạy sau và thấy người mới
        int putWonTriggerRolledBack = 0; // PUT giữ khóa, trigger lỗi @Version và rollback, lượt quét sau xử lý
        int deadlocks = 0;
        List<String> unexpected = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                Owner o = owner(T0.minusMinutes(1));   // DMS đã quá hạn, chờ quét
                User a = user("a" + round);
                User b = user("b" + round);
                User c = user("c" + round);
                assign(o, ExecutorType.PRIMARY, a, false);
                assign(o, ExecutorType.BACKUP, b, false);
                // Chờ mail của bước chuẩn bị gửi xong rồi mới xóa lượt gọi, để không lẫn vào kiểm tra của vòng.
                verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                        eq(b.getEmail()), anyString(), anyString(), any());
                clearInvocations(emailSender);
                dmsLog.list.clear();

                // PUT tới bước khóa DmsConfig nhanh hơn trigger (trigger phải quét + đọc trước), nên vòng chẵn cho
                // PUT xuất phát trễ 0–18 ms, vòng lẻ cho trigger trễ 0–4 ms, để cả hai thứ tự đều xảy ra.
                // Không ảnh hưởng assert vì test không assert bên nào thắng.
                boolean delayPut = round % 2 == 0;
                long headStartNanos = TimeUnit.MILLISECONDS.toNanos(delayPut ? 2L * (round / 2 % 10) : round / 2 % 5);
                List<Throwable> results = runConcurrently(pool, List.of(
                        () -> {
                            if (delayPut) spin(headStartNanos);
                            return assign(o, ExecutorType.PRIMARY, c, true);
                        },
                        () -> {
                            if (!delayPut) spin(headStartNanos);
                            dmsAutomationService.processOverdueDms(T0);
                            return null;
                        }));

                Throwable putError = results.get(0);
                Throwable triggerCallError = results.get(1);   // processOverdueDms tự nuốt lỗi, phải luôn null
                List<IThrowableProxy> triggerErrors = triggerFailures();

                if (isLockFailure(putError)) deadlocks++;
                for (IThrowableProxy t : triggerErrors) {
                    if (isLockFailure(t)) deadlocks++;
                }
                if (triggerCallError != null) {
                    unexpected.add("round " + round + " processOverdueDms threw " + triggerCallError);
                }

                boolean putOk = putError == null;
                // PUT thất bại phải là 409, không phải 500 / deadlock.
                assertThat(httpStatusOf(putError)).as("round %d PUT error %s", round, putError)
                        .isIn(200, 409);
                // Trigger chỉ được thua vì khóa lạc quan (một lần quét bị rollback), không lỗi lạ.
                for (IThrowableProxy t : triggerErrors) {
                    assertThat(t.getClassName()).as("round %d trigger error %s", round, t.getMessage())
                            .isEqualTo(ObjectOptimisticLockingFailureException.class.getName());
                }

                DmsStatus statusAfterRace = dmsConfigRepository.findById(o.configId).orElseThrow().getStatus();
                if (!putOk) {
                    assertThat(statusAfterRace).as("round %d: PUT 409 thì DMS phải đã TRIGGERED", round)
                            .isEqualTo(DmsStatus.TRIGGERED);
                    assertThat(triggerErrors).isEmpty();
                    triggerFirst++;
                } else if (triggerErrors.isEmpty()) {
                    assertThat(statusAfterRace).isEqualTo(DmsStatus.TRIGGERED);
                    putFirstTriggerLater++;
                } else {
                    assertThat(statusAfterRace).as("round %d: trigger rollback thì DMS vẫn ACTIVE", round)
                            .isEqualTo(DmsStatus.ACTIVE);
                    putWonTriggerRolledBack++;
                    dmsAutomationService.processOverdueDms(T0);   // "lượt quét sau"
                    assertThat(dmsConfigRepository.findById(o.configId).orElseThrow().getStatus())
                            .isEqualTo(DmsStatus.TRIGGERED);
                }

                // Không audit "ma": REPLACED đúng bằng số lần PUT thành công; ASSIGNED chỉ là 2 lần chuẩn bị.
                assertThat(auditCount(o, AuditAction.EXECUTOR_REPLACED)).as("round %d REPLACED", round)
                        .isEqualTo(putOk ? 1 : 0);
                assertThat(auditCount(o, AuditAction.EXECUTOR_ASSIGNED)).as("round %d ASSIGNED", round)
                        .isEqualTo(2);

                // DMS đã TRIGGERED -> đúng một Executor ACTIVE, và là Primary hiện hành.
                List<ExecutorAssignment> rows = executorAssignmentRepository.findAllByOwnerId(o.id);
                assertThat(rows).filteredOn(r -> r.getStatus() == ExecutorStatus.ACTIVE)
                        .as("round %d ACTIVE executors", round)
                        .singleElement()
                        .satisfies(r -> {
                            assertThat(r.getExecutorType()).isEqualTo(ExecutorType.PRIMARY);
                            assertThat(r.getExecutorUser().getId()).isEqualTo(putOk ? c.getId() : a.getId());
                        });

                if (putOk) {
                    verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                            c.getEmail(), o.fullName, o.email, ExecutorType.PRIMARY);
                    verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorRevokedNotice(
                            a.getEmail(), o.fullName, o.email, ExecutorType.PRIMARY);
                } else {
                    verify(emailSender, after(NO_MAIL_WAIT_MS).never()).sendExecutorAssignedNotice(
                            any(), any(), any(), any());
                    verify(emailSender, never()).sendExecutorRevokedNotice(any(), any(), any(), any());
                }

                deleteSeededData();
            }
        } finally {
            pool.shutdownNow();
        }
        log.info("FR-06 race: trigger first (PUT 409)={}, PUT first + trigger later={}, "
                        + "PUT won + trigger rolled back={}, deadlocks={} / {} rounds",
                triggerFirst, putFirstTriggerLater, putWonTriggerRolledBack, deadlocks, rounds);
        assertThat(unexpected).isEmpty();
        assertThat(deadlocks).as("deadlocks").isZero();
    }

    // ---------- 6. Hai request cùng gán lần đầu một vị trí ----------

    @Test
    void twoConcurrentFirstAssignments_exactlyOneRow_loserGets409() throws Exception {
        int rounds = 10;
        int uniqueViolations = 0;
        int serializedByLock = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                // Vòng chẵn: có DmsConfig -> hai bên xếp hàng trên khóa DmsConfig, bên sau thấy vị trí đã có người.
                // Vòng lẻ: chưa có DmsConfig -> không có khóa chung, bên sau đụng unique(owner_id, executor_type).
                boolean withConfig = round % 2 == 0;
                Owner o = owner(withConfig ? T0.plusDays(5) : null);
                User c1 = user("c1-" + round);
                User c2 = user("c2-" + round);

                List<Throwable> results = runConcurrently(pool, List.of(
                        () -> assign(o, ExecutorType.PRIMARY, c1, false),
                        () -> assign(o, ExecutorType.PRIMARY, c2, false)));

                assertThat(results).as("round %d", round).filteredOn(t -> t == null).hasSize(1);
                Throwable loserError = results.stream().filter(t -> t != null).findFirst().orElseThrow();
                assertThat(loserError).as("round %d loser", round).isInstanceOf(ExecutorManagementException.class);
                assertThat(((ExecutorManagementException) loserError).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                if (loserError.getMessage().contains("vừa được cập nhật")) uniqueViolations++;
                else serializedByLock++;

                User winner = results.get(0) == null ? c1 : c2;
                User loser = winner == c1 ? c2 : c1;
                assertThat(executorAssignmentRepository.findAllByOwnerId(o.id)).singleElement()
                        .satisfies(r -> assertThat(r.getExecutorUser().getId()).isEqualTo(winner.getId()));
                assertThat(auditCount(o, AuditAction.EXECUTOR_ASSIGNED)).isEqualTo(1);
                assertThat(hasExecutorRole(winner.getId())).isTrue();
                assertThat(hasExecutorRole(loser.getId())).as("role của bên thua đã rollback").isFalse();
                verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                        eq(winner.getEmail()), anyString(), anyString(), any());
                verify(emailSender, after(NO_MAIL_WAIT_MS).never()).sendExecutorAssignedNotice(
                        eq(loser.getEmail()), anyString(), anyString(), any());

                clearInvocations(emailSender);
                deleteSeededData();
            }
        } finally {
            pool.shutdownNow();
        }
        log.info("FR-06 concurrent first assignment: unique violation -> 409 = {}, serialized by DmsConfig lock = {}",
                uniqueViolations, serializedByLock);
    }

    // ---------- 7. DMS đã TRIGGERED ----------

    @Test
    void dmsTriggered_putAndDeleteRejectedWith409_noAuditNoMail() {
        Owner o = owner(T0.minusMinutes(1));
        User a = user("a");
        User b = user("b");
        User c = user("c");
        UUID primaryId = assign(o, ExecutorType.PRIMARY, a, false).getAssignmentId();
        UUID backupId = assign(o, ExecutorType.BACKUP, b, false).getAssignmentId();
        dmsAutomationService.processOverdueDms(T0);
        assertThat(dmsConfigRepository.findById(o.configId).orElseThrow().getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        verify(emailSender, timeout(MAIL_WAIT_MS)).sendExecutorAssignedNotice(
                eq(b.getEmail()), anyString(), anyString(), any());
        clearInvocations(emailSender);
        int auditsBefore = totalAuditCount(o);

        assertConflict(() -> assign(o, ExecutorType.PRIMARY, c, true));
        assertConflict(() -> assign(o, ExecutorType.BACKUP, c, true));
        assertConflict(() -> executorManagementService.revokeExecutor(o.id, ExecutorType.PRIMARY));
        assertConflict(() -> executorManagementService.revokeExecutor(o.id, ExecutorType.BACKUP));

        assertThat(totalAuditCount(o)).isEqualTo(auditsBefore);
        assertThat(executorAssignmentRepository.findById(primaryId).orElseThrow().getStatus())
                .isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(executorAssignmentRepository.findById(backupId).orElseThrow().getStatus())
                .isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(hasExecutorRole(c.getId())).isFalse();
        verify(emailSender, after(NO_MAIL_WAIT_MS).never()).sendExecutorAssignedNotice(any(), any(), any(), any());
        verify(emailSender, never()).sendExecutorRevokedNotice(any(), any(), any(), any());
    }

    // ---------- helpers ----------

    private record Owner(UUID id, String email, String fullName, UUID configId) {}

    /** Owner mới; nextCheckinDueAt = null nghĩa là KHÔNG tạo DmsConfig. */
    private Owner owner(LocalDateTime nextCheckinDueAt) {
        User owner = user("owner");
        ownerIds.add(owner.getId());
        UUID configId = null;
        if (nextCheckinDueAt != null) {
            configId = dmsConfigRepository.save(DmsConfig.builder()
                    .owner(owner)
                    .backupActivationWaitDays(14)
                    .nextCheckinDueAt(nextCheckinDueAt)
                    // Đã gửi được 1 lần nhắc: trigger không bị hoãn theo Q27 (B3).
                    .remindersSent(1)
                    .build()).getId();
            configIds.add(configId);
        }
        return new Owner(owner.getId(), owner.getEmail(), owner.getFullName(), configId);
    }

    private User user(String name) {
        String tag = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        User u = userRepository.save(User.builder()
                .email(tag + "@example.com")
                .fullName(tag)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(u.getId());
        return u;
    }

    private ExecutorAssignmentResponse assign(Owner o, ExecutorType type, User executor, boolean confirmReplace) {
        AssignExecutorRequest request = new AssignExecutorRequest();
        request.setEmail(executor.getEmail());
        request.setConfirmReplace(confirmReplace);
        return executorManagementService.assignExecutor(o.id, type, request);
    }

    private boolean hasExecutorRole(UUID userId) {
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(tx ->
                userRepository.findById(userId).orElseThrow().getRoles().stream()
                        .anyMatch(r -> "EXECUTOR".equals(r.getCode()))));
    }

    private static void assertConflict(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ExecutorManagementException.class)
                .satisfies(e -> assertThat(((ExecutorManagementException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // Mọi audit FR-06/FR-07 đều có "ownerId=..." trong metadata.detail, dùng nó để chỉ đếm audit của Owner trong test.
    private int auditCount(Owner o, AuditAction action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ?"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                Integer.class, action.name(), "%ownerId=" + o.id + "%");
    }

    private int totalAuditCount(Owner o) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                Integer.class, "%ownerId=" + o.id + "%");
    }

    /** Mã HTTP mà GlobalExceptionHandler sẽ trả cho lỗi này (200 = thành công). */
    private static int httpStatusOf(Throwable t) {
        if (t == null) return 200;
        if (t instanceof ExecutorManagementException e) return e.getStatus().value();
        if (t instanceof ObjectOptimisticLockingFailureException || t instanceof DataIntegrityViolationException) {
            return 409;
        }
        return 500;
    }

    private List<IThrowableProxy> triggerFailures() {
        List<IThrowableProxy> errors = new ArrayList<>();
        synchronized (dmsLog.list) {
            for (ILoggingEvent e : dmsLog.list) {
                if (e.getLevel() == Level.ERROR && e.getThrowableProxy() != null) {
                    errors.add(e.getThrowableProxy());
                }
            }
        }
        return Collections.unmodifiableList(errors);
    }

    private static boolean isLockFailure(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof PessimisticLockingFailureException || mentionsDeadlock(x.getClass().getName(), x.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLockFailure(IThrowableProxy t) {
        for (IThrowableProxy x = t; x != null; x = x.getCause()) {
            if (mentionsDeadlock(x.getClassName(), x.getMessage())
                    || x.getClassName().endsWith("CannotAcquireLockException")
                    || x.getClassName().endsWith("PessimisticLockingFailureException")) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsDeadlock(String className, String message) {
        return className.contains("Deadlock") || className.contains("LockAcquisition")
                || (message != null && (message.contains("Deadlock") || message.contains("Lock wait timeout")));
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
