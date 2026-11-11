package com.ltld.app.legacyvault.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.DmsCheckinLinkException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2 (t1): check-in một chạm trên MySQL thật: token dùng 1 lần (UNIQUE token_jti thật), token chu kỳ cũ bị từ chối
 * (m7), bấm đúp đồng thời chỉ check-in 1 lần (m1: kiểm jti sau khi khóa DmsConfig), audit DMS_CHECKIN_ONE_CLICK (m2).
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
class DmsOneClickCheckinIntegrationTest extends MySqlTestContainer {

    @Autowired private DmsOneClickCheckinService oneClickCheckinService;
    @Autowired private DmsConfigService dmsConfigService;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private DmsCheckinRepository dmsCheckinRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID configId;
    private LocalDateTime initialCheckin;

    @BeforeEach
    void seed() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(User.builder().email("owner-" + tag + "@example.com").fullName("Owner")
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        ownerId = owner.getId();
        // Check-in trong app yêu cầu Owner còn ít nhất 1 vault (DMS_NO_VAULT)
        vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        initialCheckin = LocalDateTime.now().minusDays(28).truncatedTo(ChronoUnit.SECONDS);
        configId = dmsConfigRepository.save(DmsConfig.builder()
                .owner(owner)
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .lastCheckinAt(initialCheckin)
                .nextCheckinDueAt(initialCheckin.plusDays(37))
                .remindersSent(2)
                .build()).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                "%ownerId=" + ownerId + "%");
        dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(configId));
        dmsConfigRepository.deleteById(configId);
        vaultRepository.deleteAll(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId));
        userRepository.deleteById(ownerId);
    }

    @Test
    void validToken_checksInOnce_secondUseRejected() {
        String token = oneClickCheckinService.generateCheckinTokenForConfig(configId);

        oneClickCheckinService.checkInWithToken(token);

        DmsConfig after = config();
        assertThat(after.getLastCheckinAt()).isAfter(initialCheckin);
        assertThat(after.getRemindersSent()).isZero();
        List<DmsCheckin> checkins = ownerResponded();
        assertThat(checkins).singleElement().satisfies(c -> {
            assertThat(c.getTokenJti()).isEqualTo(jti(token));
            assertThat(c.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        });
        assertThat(auditActions()).containsExactly(AuditAction.DMS_CHECKIN_ONE_CLICK);

        // Dùng lại đúng token đó: bị từ chối, không reset chu kỳ lần nữa
        assertReason(() -> oneClickCheckinService.checkInWithToken(token), DmsCheckinLinkException.Reason.TOKEN_INVALID);
        assertThat(config().getLastCheckinAt()).isEqualTo(after.getLastCheckinAt());
        assertThat(ownerResponded()).hasSize(1);
    }

    @Test
    void tokenFromEarlierCycle_isRejected_afterAnotherCheckin() {
        // Hai lần nhắc trong cùng chu kỳ -> hai link; dùng link 1 thì link 2 thuộc chu kỳ cũ (m7)
        String first = oneClickCheckinService.generateCheckinTokenForConfig(configId);
        String second = oneClickCheckinService.generateCheckinTokenForConfig(configId);
        oneClickCheckinService.checkInWithToken(first);

        assertReason(() -> oneClickCheckinService.checkInWithToken(second), DmsCheckinLinkException.Reason.TOKEN_INVALID);
        assertThat(ownerResponded()).hasSize(1);
    }

    @Test
    void tokenIssuedBeforeInAppCheckin_isRejected() {
        String token = oneClickCheckinService.generateCheckinTokenForConfig(configId);
        dmsConfigService.checkIn(ownerId);

        assertReason(() -> oneClickCheckinService.checkInWithToken(token), DmsCheckinLinkException.Reason.TOKEN_INVALID);
        assertThat(auditActions()).containsExactly(AuditAction.DMS_CHECKIN);
    }

    @Test
    void triggeredDms_isRejectedWithTriggeredReason_andNothingChanges() {
        String token = oneClickCheckinService.generateCheckinTokenForConfig(configId);
        DmsConfig config = config();
        config.setStatus(DmsStatus.TRIGGERED);
        dmsConfigRepository.save(config);

        assertReason(() -> oneClickCheckinService.checkInWithToken(token), DmsCheckinLinkException.Reason.TRIGGERED);
        assertThat(config().getLastCheckinAt()).isEqualTo(initialCheckin);
        assertThat(ownerResponded()).isEmpty();
    }

    // X2: đang chờ người xác nhận chéo thì Owner bấm link vẫn check-in được, DMS về ACTIVE (như checkIn trong app)
    @Test
    void pendingConfirmation_checkInByLink_returnsToActive() {
        String token = oneClickCheckinService.generateCheckinTokenForConfig(configId);
        DmsConfig config = config();
        config.setStatus(DmsStatus.PENDING_CONFIRMATION);
        dmsConfigRepository.save(config);

        oneClickCheckinService.checkInWithToken(token);

        assertThat(config().getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(config().getLastCheckinAt()).isAfter(initialCheckin);
        assertThat(auditActions()).containsExactly(AuditAction.DMS_CHECKIN_ONE_CLICK);
    }

    @Test
    void tokenJti_isUniqueInDatabase() {
        String jti = UUID.randomUUID().toString();
        DmsConfig config = config();
        dmsCheckinRepository.saveAndFlush(checkin(config, jti));

        assertThatThrownBy(() -> dmsCheckinRepository.saveAndFlush(checkin(config, jti)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // m1: bấm đúp / trình quét + người bấm gửi cùng token đồng thời -> đúng 1 lần check-in, lần kia TOKEN_INVALID
    @Test
    void concurrentSameToken_checksInExactlyOnce() throws Exception {
        String token = oneClickCheckinService.generateCheckinTokenForConfig(configId);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> task = () -> {
            start.await();
            try {
                return oneClickCheckinService.checkInWithToken(token);
            } catch (DmsCheckinLinkException e) {
                return e.getReason();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            futures.add(pool.submit(task));
            futures.add(pool.submit(task));
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(r -> r == DmsCheckinLinkException.Reason.TOKEN_INVALID).hasSize(1);
            assertThat(results).filteredOn(r -> !(r instanceof DmsCheckinLinkException.Reason)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(ownerResponded()).hasSize(1);
        assertThat(auditActions()).containsExactly(AuditAction.DMS_CHECKIN_ONE_CLICK);
    }

    private static void assertReason(Runnable call, DmsCheckinLinkException.Reason reason) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(DmsCheckinLinkException.class, e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    private static DmsCheckin checkin(DmsConfig config, String jti) {
        return DmsCheckin.builder().dmsConfig(config).checkinType(DmsCheckinType.OWNER_RESPONDED)
                .channel(NotificationChannel.EMAIL).tokenJti(jti).occurredAt(LocalDateTime.now()).build();
    }

    private DmsConfig config() {
        return dmsConfigRepository.findById(configId).orElseThrow();
    }

    private List<DmsCheckin> ownerResponded() {
        return dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(configId).stream()
                .filter(c -> c.getCheckinType() == DmsCheckinType.OWNER_RESPONDED)
                .toList();
    }

    private List<AuditAction> auditActions() {
        return auditLogRepository.findByActor_IdOrderByCreatedAtDesc(ownerId, Pageable.unpaged()).stream()
                .map(AuditLog::getAction)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static String jti(String token) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
            return (String) new ObjectMapper().readValue(payload, Map.class).get("jti");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
