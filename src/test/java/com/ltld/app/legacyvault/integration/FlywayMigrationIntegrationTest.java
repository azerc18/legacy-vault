package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    @Autowired private Flyway flyway;

    @Test
    void migrationsApplyOnEmptyDb_andMatchEntities() {
        // Context lên được nghĩa là validate đã qua
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }
}