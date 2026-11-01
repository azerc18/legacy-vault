package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** H0: DB trống -> Flyway chạy toàn bộ migration -> Hibernate validate phải khớp entity. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false",
        "app.beneficiary.claim-expiry.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean private EmailSender emailSender;
    @MockitoBean private ExecutorNotificationService executorNotificationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private Flyway flyway;

    @Test
    void migrationsApplyOnEmptyDb_andMatchEntities() {
        // Context lên được nghĩa là validate đã qua
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void auditLogsAction_isVarchar() {
        String type = jdbcTemplate.queryForObject("""
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND COLUMN_NAME = 'action'
                """, String.class);
        assertThat(type).isEqualTo("varchar");
    }

    @Test
    void auditLogsAction_acceptsAnyEnumName() {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, action, created_at)
                VALUES (UUID_TO_BIN(UUID()), ?, NOW(6))
                """, "SOME_FUTURE_ACTION");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'SOME_FUTURE_ACTION'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // N2b: mọi ClaimStatus phải có trong cột DB, nếu không Decline sẽ lỗi "Data truncated"
    @Test
    void claimStatus_columnAcceptsEveryJavaEnumValue() {
        String claimType = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beneficiary_claims' AND COLUMN_NAME = 'status'
                """, String.class);
        for (ClaimStatus s : ClaimStatus.values()) {
            assertThat(claimType).contains("'" + s.name() + "'");
        }
    }
}