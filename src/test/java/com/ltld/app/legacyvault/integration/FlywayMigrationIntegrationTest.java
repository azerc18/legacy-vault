package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
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
        "app.beneficiary.claim-expiry.enabled=false",
        "app.legacy-message.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

    // N3: mọi AssetType phải có trong cột digital_assets.asset_type
    @Test
    void assetType_columnAcceptsEveryJavaEnumValue() {
        String col = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'digital_assets' AND COLUMN_NAME = 'asset_type'
                """, String.class);
        for (AssetType t : AssetType.values()) {
            assertThat(col).contains("'" + t.name() + "'");
        }
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

    // Xoá tài khoản: mọi UserStatus (kể cả DELETED) phải có trong enum users.status, và có cột deleted_at
    @Test
    void userStatus_columnAcceptsEveryJavaEnumValue_andHasDeletedAt() {
        String col = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'status'
                """, String.class);
        for (UserStatus s : UserStatus.values()) {
            assertThat(col).contains("'" + s.name() + "'");
        }
        Integer deletedAt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'deleted_at'
                """, Integer.class);
        assertThat(deletedAt).isEqualTo(1);
    }

    // X2: mọi DmsStatus (kể cả PENDING_CONFIRMATION) phải có trong enum dms_configs.status; validate không kiểm giá trị enum
    @Test
    void dmsStatus_columnAcceptsEveryJavaEnumValue() {
        String col = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_configs' AND COLUMN_NAME = 'status'
                """, String.class);
        for (DmsStatus s : DmsStatus.values()) {
            assertThat(col).contains("'" + s.name() + "'");
        }
    }

    // X2: mọi DmsConfirmerStatus phải có trong enum dms_confirmers.status
    @Test
    void dmsConfirmerStatus_columnAcceptsEveryJavaEnumValue() {
        String col = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dms_confirmers' AND COLUMN_NAME = 'status'
                """, String.class);
        for (DmsConfirmerStatus s : DmsConfirmerStatus.values()) {
            assertThat(col).contains("'" + s.name() + "'");
        }
    }

    // F4b: mọi NotificationType (kể cả LEGACY_MESSAGE_DELIVERED) phải có trong enum notifications.type
    @Test
    void notificationType_columnAcceptsEveryJavaEnumValue() {
        String col = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'type'
                """, String.class);
        for (NotificationType t : NotificationType.values()) {
            assertThat(col).contains("'" + t.name() + "'");
        }
    }
}
