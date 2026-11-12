package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.AdminDmsReminderResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditMailListener;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin chủ động nhắc Owner check-in DMS, chạy trên MySQL thật. */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.dms.admin-reminder-cooldown-minutes=60"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminDmsReminderIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean private EmailSender emailSender;
    @MockitoBean private ExecutorNotificationService executorNotificationService;
    // Mail theo audit chạy @Async và gọi emailSender: tách ra để doThrow(..).when(emailSender) không bị chen ngang.
    @MockitoBean private AuditMailListener auditMailListener;

    @Autowired private AdminDmsReminderService adminDmsReminderService;
    @Autowired private DmsConfigService dmsConfigService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private DmsCheckinRepository dmsCheckinRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> ownerIds = new ArrayList<>();
    private final List<UUID> otherUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID ownerId : ownerIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                    "%ownerId=" + ownerId + "%");
            dmsConfigRepository.findByOwnerId(ownerId).ifPresent(c -> {
                dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(c.getId()));
                dmsConfigRepository.delete(c);
            });
            executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(ownerId));
            vaultRepository.deleteAll(vaultRepository.findAll().stream()
                    .filter(v -> v.getOwner().getId().equals(ownerId)).toList());
        }
        List<UUID> all = new ArrayList<>(ownerIds);
        all.addAll(otherUserIds);
        notificationRepository.deleteAll(notificationRepository.findAll().stream()
                .filter(n -> all.contains(n.getRecipient().getId())).toList());
        userRepository.deleteAllById(all);
        ownerIds.clear();
        otherUserIds.clear();
    }

    @Test
    void remind_activeOwner_sendsBellAndMail_keepsAutomaticSchedule() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        DmsConfig before = config(ownerId);

        AdminDmsReminderResponse res = adminDmsReminderService.remind(ownerId, adminId);

        assertThat(res.isEmailSent()).isTrue();
        assertThat(res.getNextAllowedAt()).isEqualTo(res.getSentAt().plusMinutes(60));
        assertThat(res.getTriggerAt()).isEqualTo(before.getNextCheckinDueAt());
        verify(emailSender).sendDmsAdminCheckinReminder(eq(email(ownerId)), any(), eq(before.getNextCheckinDueAt()),
                eq(false), argThat(t -> t != null && t.split("\\.").length == 3));

        DmsConfig after = config(ownerId);
        assertThat(after.getLastAdminReminderAt()).isEqualTo(res.getSentAt());
        // Lịch nhắc tự động không đổi
        assertThat(after.getRemindersSent()).isEqualTo(before.getRemindersSent());
        assertThat(after.getNextReminderAt()).isEqualTo(before.getNextReminderAt());
        assertThat(after.getNextCheckinDueAt()).isEqualTo(before.getNextCheckinDueAt());

        List<Notification> bell = notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(ownerId).stream()
                .filter(n -> n.getType() == NotificationType.DMS_CHECKIN_REMINDER).toList();
        assertThat(bell).hasSize(1);
        assertThat(bell.get(0).getParams()).contains("\"variant\":\"admin\"");

        assertThat(auditLogRepository.findByActor_IdOrderByCreatedAtDesc(adminId, Pageable.unpaged()))
                .extracting(AuditLog::getAction).containsExactly(AuditAction.DMS_ADMIN_CHECKIN_REMINDER);
    }

    @Test
    void remindTwiceWithinCooldown_secondReturns429AndSendsNothing() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        adminDmsReminderService.remind(ownerId, adminId);

        assertThatThrownBy(() -> adminDmsReminderService.remind(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .satisfies(e -> {
                    assertThat(((DmsException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(((DmsException) e).getErrorCode()).isEqualTo(ErrorCode.DMS_ADMIN_REMINDER_TOO_SOON);
                });
        verify(emailSender, times(1)).sendDmsAdminCheckinReminder(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void remindAfterCooldown_isAllowedAgain() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        adminDmsReminderService.remind(ownerId, adminId);
        DmsConfig config = config(ownerId);
        config.setLastAdminReminderAt(LocalDateTime.now().minusMinutes(61));
        dmsConfigRepository.saveAndFlush(config);

        assertThat(adminDmsReminderService.remind(ownerId, adminId).isEmailSent()).isTrue();
        verify(emailSender, times(2)).sendDmsAdminCheckinReminder(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void mailFails_bellStays_reminderReleasedSoAdminCanRetry() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        doThrow(new MailSendException("smtp down"))
                .when(emailSender).sendDmsAdminCheckinReminder(any(), any(), any(), anyBoolean(), any());

        AdminDmsReminderResponse res = adminDmsReminderService.remind(ownerId, adminId);

        assertThat(res.isEmailSent()).isFalse();
        assertThat(res.getNextAllowedAt()).isNull();
        assertThat(config(ownerId).getLastAdminReminderAt()).isNull();
        assertThat(notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(ownerId))
                .anyMatch(n -> n.getType() == NotificationType.DMS_CHECKIN_REMINDER);

        doNothing().when(emailSender).sendDmsAdminCheckinReminder(any(), any(), any(), anyBoolean(), any());
        assertThat(adminDmsReminderService.remind(ownerId, adminId).isEmailSent()).isTrue();
        // Bấm lại sau khi mail lỗi không ghi thêm chuông giống hệt
        assertThat(adminBells(ownerId)).hasSize(1);
        // Audit: lần lỗi là FAILURE, lần gửi được là SUCCESS
        assertThat(auditLogRepository.findByActor_IdOrderByCreatedAtDesc(adminId, Pageable.unpaged()))
                .extracting(AuditLog::getAction).containsOnly(AuditAction.DMS_ADMIN_CHECKIN_REMINDER).hasSize(2);
    }

    @Test
    void mailFailsAfterEarlierSuccess_restoresPreviousReminderTime() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        LocalDateTime earlier = LocalDateTime.now().minusMinutes(90).truncatedTo(ChronoUnit.MICROS);
        DmsConfig config = config(ownerId);
        config.setLastAdminReminderAt(earlier);
        dmsConfigRepository.saveAndFlush(config);
        doThrow(new MailSendException("smtp down"))
                .when(emailSender).sendDmsAdminCheckinReminder(any(), any(), any(), anyBoolean(), any());

        assertThat(adminDmsReminderService.remind(ownerId, adminId).isEmailSent()).isFalse();

        assertThat(config(ownerId).getLastAdminReminderAt()).isEqualTo(earlier);
    }

    @Test
    void twoSuccessfulRemindersAfterCooldown_writeTwoBells() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        adminDmsReminderService.remind(ownerId, adminId);
        DmsConfig config = config(ownerId);
        config.setLastAdminReminderAt(LocalDateTime.now().minusMinutes(61).truncatedTo(ChronoUnit.MICROS));
        dmsConfigRepository.saveAndFlush(config);

        adminDmsReminderService.remind(ownerId, adminId);

        assertThat(adminBells(ownerId)).hasSize(2);
    }

    @Test
    void secondsUntilAllowed_countsDownFromLastReminder() {
        assertThat(adminDmsReminderService.secondsUntilAllowed(null)).isNull();
        assertThat(adminDmsReminderService.secondsUntilAllowed(LocalDateTime.now().minusMinutes(61))).isNull();
        assertThat(adminDmsReminderService.secondsUntilAllowed(LocalDateTime.now().minusMinutes(50)))
                .isBetween(590L, 600L);
    }

    @Test
    void suspendedOwner_returns409() {
        UUID ownerId = ownerWithDms();
        UUID adminId = admin();
        dmsConfigService.suspendByAdmin(ownerId, adminId, "kiểm tra");

        assertThatThrownBy(() -> adminDmsReminderService.remind(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(emailSender, never()).sendDmsAdminCheckinReminder(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void ownerWithoutDms_returns404() {
        UUID ownerId = user("nodms").getId();
        ownerIds.add(ownerId);
        UUID adminId = admin();

        assertThatThrownBy(() -> adminDmsReminderService.remind(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private UUID ownerWithDms() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = user("owner-" + tag);
        ownerIds.add(owner.getId());
        vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        User primary = user("primary-" + tag);
        otherUserIds.add(primary.getId());
        executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner).executorUser(primary).executorType(ExecutorType.PRIMARY).build());
        DmsConfigRequest request = new DmsConfigRequest();
        request.setCheckinIntervalDays(30);
        request.setGracePeriodDays(7);
        request.setReminderCount(3);
        dmsConfigService.upsertConfig(owner.getId(), request);
        clearInvocations(emailSender);
        return owner.getId();
    }

    private UUID admin() {
        UUID id = user("admin-" + UUID.randomUUID().toString().substring(0, 8)).getId();
        otherUserIds.add(id);
        return id;
    }

    private User user(String name) {
        return userRepository.save(User.builder()
                .kycLevel(KycLevel.BASIC)
                .email(name + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
    }

    private DmsConfig config(UUID ownerId) {
        return dmsConfigRepository.findByOwnerId(ownerId).orElseThrow();
    }

    private List<Notification> adminBells(UUID ownerId) {
        return notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(ownerId).stream()
                .filter(n -> n.getType() == NotificationType.DMS_CHECKIN_REMINDER)
                .filter(n -> n.getParams() != null && n.getParams().contains("\"variant\":\"admin\""))
                .toList();
    }

    private String email(UUID userId) {
        return userRepository.findById(userId).orElseThrow().getEmail();
    }
}
