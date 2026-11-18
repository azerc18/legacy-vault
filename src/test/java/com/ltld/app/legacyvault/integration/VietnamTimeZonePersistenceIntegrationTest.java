package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.config.AppTimeZone;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditMailListener;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JVM chạy giờ VN (như production sau AppTimeZone) nhưng DB vẫn phải lưu UTC, để dữ liệu cũ (UTC) không bị lệch
 * và scheduler so mốc giờ đúng.
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(initializers = VietnamTimeZonePersistenceIntegrationTest.VietnamTimeZoneInitializer.class)
class VietnamTimeZonePersistenceIntegrationTest {

    private static final TimeZone ORIGINAL = TimeZone.getDefault();

    /**
     * Như LegacyVaultApplication.main: đặt giờ VN trước khi Hibernate khởi động. Đặt trong initializer (không phải
     * static {}) để khi máy không có Docker và class bị bỏ qua thì múi giờ JVM không bị đổi cho các test sau.
     */
    static class VietnamTimeZoneInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TimeZone.setDefault(TimeZone.getTimeZone(AppTimeZone.VN));
        }
    }

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean private EmailSender emailSender;
    @MockitoBean private ExecutorNotificationService executorNotificationService;
    @MockitoBean private AuditMailListener auditMailListener;

    @Autowired private UserRepository userRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(ORIGINAL);
    }

    @Test
    void vietnamLocalDateTime_isStoredAsUtc_andReadBackAsVietnamTime() {
        User owner = userRepository.save(User.builder().email("tz-owner@example.com").fullName("TZ Owner")
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        LocalDateTime checkinVn = LocalDateTime.of(2026, 11, 18, 16, 50, 0);
        DmsConfig saved = dmsConfigRepository.save(DmsConfig.builder().owner(owner)
                .lastCheckinAt(checkinVn).nextCheckinDueAt(checkinVn.plusDays(10)).build());

        String raw = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(last_checkin_at, '%Y-%m-%d %H:%i:%s') FROM dms_configs WHERE owner_id = "
                        + "(SELECT id FROM users WHERE email = 'tz-owner@example.com')", String.class);
        assertThat(raw).isEqualTo("2026-11-18 09:50:00");

        DmsConfig reloaded = dmsConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLastCheckinAt()).isEqualTo(checkinVn);
        assertThat(reloaded.getNextCheckinDueAt()).isEqualTo(checkinVn.plusDays(10));
    }

    @Test
    void existingUtcRow_isReadAsVietnamTime() {
        User owner = userRepository.save(User.builder().email("tz-legacy@example.com").fullName("TZ Legacy")
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        DmsConfig saved = dmsConfigRepository.save(DmsConfig.builder().owner(owner).build());
        // Dòng cũ ghi khi server còn chạy UTC: 09:50 UTC = 16:50 giờ VN
        jdbcTemplate.update("UPDATE dms_configs SET last_checkin_at = '2026-11-18 09:50:00' WHERE owner_id = "
                + "(SELECT id FROM users WHERE email = 'tz-legacy@example.com')");

        DmsConfig reloaded = dmsConfigRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLastCheckinAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 16, 50, 0));
    }
}
