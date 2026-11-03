package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerRequest;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.integration.MySqlTestContainer;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * X2 trên MySQL thật: DMS quá hạn -> PENDING_CONFIRMATION -> mail gửi SAU commit (1 token/confirmer) -> người xác nhận
 * bấm "alive" -> Owner được nhắc lại theo lịch nhắc trước hạn mới (7 ngày), rồi dừng; hết hạn mà vẫn không check-in thì
 * kích hoạt luôn (ALIVE chỉ 1 lần/đợt). Không gửi được mail cho ai thì kích hoạt như luồng cũ.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.dms.scheduler.enabled=false"
})
// n9: dùng container MySQL chung (MySqlTestContainer) như các integration test khác
class DmsConfirmerIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;                                     // không gửi mail thật
    @MockitoBean
    private ExecutorNotificationService executorNotificationService;   // không gửi mail thật

    @Autowired private DmsConfigService dmsConfigService;
    @Autowired private DmsConfirmerService dmsConfirmerService;
    @Autowired private DmsReminderService dmsReminderService;
    @Autowired private DmsAutomationService dmsAutomationService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private DmsCheckinRepository dmsCheckinRepository;
    @Autowired private DmsConfirmerRepository dmsConfirmerRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final int INTERVAL_DAYS = 30;
    private static final int GRACE_DAYS = 7;
    private static final int REMINDERS = 3;

    private final List<UUID> ownerIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID ownerId : ownerIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                    "%ownerId=" + ownerId + "%");
            dmsConfigRepository.findByOwnerId(ownerId).ifPresent(c -> {
                dmsConfirmerRepository.deleteAll(dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(c.getId()));
                dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(c.getId()));
                dmsConfigRepository.delete(c);
            });
            executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(ownerId));
            vaultRepository.deleteAll(vaultRepository.findAll().stream()
                    .filter(v -> v.getOwner().getId().equals(ownerId))
                    .toList());
        }
        // DMS đã kích hoạt thì có thông báo gửi Executor (FK recipient_id -> users): xoá trước khi xoá user
        notificationRepository.deleteAll(notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient() != null && userIds.contains(n.getRecipient().getId()))
                .toList());
        userRepository.deleteAllById(userIds);
        ownerIds.clear();
        userIds.clear();
    }

    @Test
    void confirmedAlive_remindsOwnerBeforeNewDeadline_thenStops_andTriggersAtDeadline() {
        UUID ownerId = seedOwnerWithConfirmer();
        String ownerEmail = userRepository.findById(ownerId).orElseThrow().getEmail();
        LocalDateTime realLastCheckin = config(ownerId).getLastCheckinAt();
        LocalDateTime t0 = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        // DMS quá hạn -> chờ xác nhận chéo; mail gửi sau commit, lấy token thật trong mail gửi người xác nhận.
        dmsAutomationService.processOverdueDms(t0);
        assertThat(config(ownerId).getStatus()).isEqualTo(DmsStatus.PENDING_CONFIRMATION);
        String token = lastConfirmationToken();
        // m5: Owner được báo là người xác nhận đang được liên hệ
        verify(emailSender).sendOwnerConfirmationPendingNotice(eq(ownerEmail), any(), anyList(), any());

        dmsConfirmerService.respondByToken(token, DmsConfirmerDecision.ALIVE, t0);

        LocalDateTime deadline = t0.plusDays(DmsConfirmerServiceImpl.POSTPONE_SAFE_DAYS);
        DmsConfig afterAlive = config(ownerId);
        assertThat(afterAlive.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(afterAlive.getNextCheckinDueAt()).isEqualTo(deadline);
        assertThat(afterAlive.getRemindersSent()).isZero();
        assertThat(afterAlive.getNextReminderAt()).isNotNull().isBefore(deadline);
        assertThat(afterAlive.getConfirmationDeferrals()).isEqualTo(1);
        assertThat(afterAlive.getLastCheckinAt()).isEqualTo(realLastCheckin);   // m3: không giả lần check-in
        assertThat(afterAlive.getCycleStartedAt()).isEqualTo(deadline.minusDays(INTERVAL_DAYS + GRACE_DAYS));

        // Quét nhắc đúng các mốc nextReminderAt: mỗi mốc gửi 1 mail, tất cả trước hạn; sau lần cuối thì hết mốc.
        List<LocalDateTime> scans = new ArrayList<>();
        for (int i = 0; i < REMINDERS + 5 && config(ownerId).getNextReminderAt() != null; i++) {
            LocalDateTime scan = config(ownerId).getNextReminderAt();
            scans.add(scan);
            dmsReminderService.sendDueReminders(scan);
        }
        assertThat(scans).hasSize(REMINDERS).allSatisfy(t -> assertThat(t).isBefore(deadline));
        assertThat(config(ownerId).getNextReminderAt()).isNull();
        assertThat(config(ownerId).getRemindersSent()).isEqualTo(REMINDERS);
        for (int i = 1; i <= REMINDERS; i++) {
            verify(emailSender).sendDmsCheckinReminder(eq(ownerEmail), any(), eq(deadline), eq(i), eq(REMINDERS));
        }

        // Quét lại sát hạn: không gửi thêm (không nhắc lặp).
        dmsReminderService.sendDueReminders(deadline.minusMinutes(1));
        verify(emailSender, times(REMINDERS)).sendDmsCheckinReminder(anyString(), any(), any(), anyInt(), anyInt());

        // m2: hết hạn mới mà Owner vẫn im lặng -> kích hoạt luôn, KHÔNG hỏi xác nhận lần 2.
        dmsAutomationService.processOverdueDms(deadline.plusMinutes(1));
        assertThat(config(ownerId).getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        verify(emailSender, times(1)).sendDmsCrossConfirmationRequest(
                anyString(), any(), any(), any(), any(), anyString());
        verify(executorNotificationService).send(anyString(), anyString());
    }

    @Test
    void ownerCheckIn_resetsAliveCounter_soNextOverdueAsksConfirmersAgain() {
        UUID ownerId = seedOwnerWithConfirmer();
        LocalDateTime t0 = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        dmsAutomationService.processOverdueDms(t0);
        dmsConfirmerService.respondByToken(lastConfirmationToken(), DmsConfirmerDecision.ALIVE, t0);
        assertThat(config(ownerId).getConfirmationDeferrals()).isEqualTo(1);

        dmsConfigService.checkIn(ownerId);

        DmsConfig afterCheckIn = config(ownerId);
        assertThat(afterCheckIn.getConfirmationDeferrals()).isZero();
        assertThat(afterCheckIn.getCycleStartedAt()).isEqualTo(afterCheckIn.getLastCheckinAt());

        // Đợt mới quá hạn -> lại hỏi người xác nhận
        LocalDateTime nextOverdue = afterCheckIn.getNextCheckinDueAt().plusMinutes(1);
        afterCheckIn.setRemindersSent(1);   // đã nhắc được, không hoãn Q27
        dmsConfigRepository.saveAndFlush(afterCheckIn);
        dmsAutomationService.processOverdueDms(nextOverdue);
        assertThat(config(ownerId).getStatus()).isEqualTo(DmsStatus.PENDING_CONFIRMATION);
    }

    @Test
    void noConfirmationEmailSent_triggersAsUsual_withAudit() {
        UUID ownerId = seedOwnerWithConfirmer();
        doThrow(new IllegalStateException("SMTP down")).when(emailSender)
                .sendDmsCrossConfirmationRequest(anyString(), any(), any(), any(), any(), anyString());

        dmsAutomationService.processOverdueDms(LocalDateTime.now());

        DmsConfig after = config(ownerId);
        assertThat(after.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        verify(executorNotificationService).send(anyString(), anyString());
        verify(emailSender, never()).sendOwnerConfirmationPendingNotice(anyString(), any(), anyList(), any());
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'DMS_CONFIRMATION_UNDELIVERED'"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                Integer.class, "%ownerId=" + ownerId + "%");
        assertThat(audits).isEqualTo(1);
    }

    // ---------- helpers ----------

    /** Owner có vault, Primary + Backup, DMS 30/7/3 đã quá hạn (đã nhận 1 lần nhắc nên không hoãn Q27), 1 người xác nhận. */
    private UUID seedOwnerWithConfirmer() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = user("owner-" + tag);
        ownerIds.add(owner.getId());
        vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        assignment(owner, user("primary-" + tag), ExecutorType.PRIMARY);
        assignment(owner, user("backup-" + tag), ExecutorType.BACKUP);

        DmsConfigRequest request = new DmsConfigRequest();
        request.setCheckinIntervalDays(INTERVAL_DAYS);
        request.setGracePeriodDays(GRACE_DAYS);
        request.setReminderCount(REMINDERS);
        dmsConfigService.upsertConfig(owner.getId(), request);
        dmsConfirmerService.addConfirmer(owner.getId(), DmsConfirmerRequest.builder()
                .name("Confirmer " + tag).email("confirmer-" + tag + "@example.com").relationship("Spouse").build());

        DmsConfig config = config(owner.getId());
        LocalDateTime start = LocalDateTime.now().minusDays(INTERVAL_DAYS + GRACE_DAYS + 1L).truncatedTo(ChronoUnit.MICROS);
        config.setLastCheckinAt(start);
        config.setCycleStartedAt(start);
        config.setNextCheckinDueAt(start.plusDays(INTERVAL_DAYS + GRACE_DAYS));
        config.setNextReminderAt(null);
        config.setRemindersSent(1);
        dmsConfigRepository.saveAndFlush(config);
        return owner.getId();
    }

    /** Token trong mail xác nhận gần nhất (mail gửi sau commit, tham số cuối của sendDmsCrossConfirmationRequest). */
    private String lastConfirmationToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).sendDmsCrossConfirmationRequest(
                anyString(), any(), any(), any(), any(), token.capture());
        return token.getValue();
    }

    private DmsConfig config(UUID ownerId) {
        return dmsConfigRepository.findByOwnerId(ownerId).orElseThrow();
    }

    private User user(String name) {
        User u = userRepository.save(User.builder()
                .email(name + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(u.getId());
        return u;
    }

    private void assignment(User owner, User executor, ExecutorType type) {
        executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executor)
                .executorType(type)
                .build());
    }
}
