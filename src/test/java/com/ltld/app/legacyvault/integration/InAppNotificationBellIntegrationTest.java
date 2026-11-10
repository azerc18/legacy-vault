package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.notificationdto.NotificationResponse;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotificationService;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Chuông thông báo trên MySQL thật: sự kiện audit sau commit sinh thông báo trong app, rollback thì không;
// đọc lại qua InAppNotificationService (params, tab chưa đọc, đánh dấu đã đọc tất cả).
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false",
        "app.beneficiary.claim-expiry.enabled=false"
})
class InAppNotificationBellIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;            // không gửi mail thật

    @Autowired private AuditLogService auditLogService;
    @Autowired private InAppNotificationService inAppNotificationService;
    @Autowired private VaultService vaultService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private User beneficiary;
    private Vault vault;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("bell-owner@example.com").fullName("Nguyễn Văn An").passwordHash("x")
                .status(UserStatus.ACTIVE).build());
        beneficiary = userRepository.save(User.builder()
                .email("bell-bene@example.com").fullName("Bene").passwordHash("x").status(UserStatus.ACTIVE).build());
        vault = vaultRepository.save(Vault.builder().owner(owner).name("Két \"gia đình\"")
                .status(VaultStatus.ACTIVE).build());
    }

    @AfterEach
    void cleanUp() {
        // Con trước, cha sau (theo FK)
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private void logAssigned() {
        auditLogService.log(AuditAction.BENEFICIARY_ASSIGNED, AuditResult.SUCCESS, owner.getId(), owner.getEmail(),
                vault.getId(), "Vault", vault.getId().toString(), "beneficiaryUserId=" + beneficiary.getId());
    }

    @Test
    void beneficiaryAssigned_afterCommit_appearsInTheBell_thenMarkAllReadClearsIt() {
        transactionTemplate.executeWithoutResult(status -> logAssigned());

        assertThat(inAppNotificationService.countUnread(beneficiary.getId())).isEqualTo(1);
        NotificationResponse n = inAppNotificationService.listMine(beneficiary.getId(), 0, 20, true)
                .getContent().get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.BENEFICIARY_ASSIGNED);
        assertThat(n.getReferenceType()).isEqualTo("Vault");
        assertThat(n.getReferenceId()).isEqualTo(vault.getId());
        assertThat(n.getParams()).containsEntry("owner", "Nguyễn Văn An").containsEntry("vault", "Két \"gia đình\"");
        assertThat(n.isRead()).isFalse();
        // Chỉ người được chỉ định thấy thông báo, Owner thì không
        assertThat(inAppNotificationService.countUnread(owner.getId())).isZero();

        assertThat(inAppNotificationService.markAllRead(beneficiary.getId())).isEqualTo(1);

        assertThat(inAppNotificationService.countUnread(beneficiary.getId())).isZero();
        assertThat(inAppNotificationService.listMine(beneficiary.getId(), 0, 20, true).getContent()).isEmpty();
        assertThat(inAppNotificationService.listMine(beneficiary.getId(), 0, 20).getContent())
                .singleElement().matches(NotificationResponse::isRead);
    }

    @Test
    void rolledBackAction_leavesNoNotification() {
        transactionTemplate.executeWithoutResult(status -> {
            logAssigned();
            status.setRollbackOnly();
        });

        assertThat(inAppNotificationService.listMine(beneficiary.getId(), 0, 20).getContent()).isEmpty();
    }

    @Test
    void removeBeneficiary_notifiesTheRemovedAccount_withoutALinkToTheVault() {
        vaultBeneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(vault).email("someone-else@example.com")
                .status(BeneficiaryInviteStatus.PENDING_REGISTRATION).build());
        VaultBeneficiary row = vaultBeneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(vault).user(beneficiary).email(beneficiary.getEmail())
                .status(BeneficiaryInviteStatus.INVITED).build());

        vaultService.removeBeneficiary(owner.getId(), vault.getId(), row.getId());

        List<NotificationResponse> list = inAppNotificationService.listMine(beneficiary.getId(), 0, 20).getContent();
        assertThat(list).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.BENEFICIARY_REMOVED);
            assertThat(n.getReferenceId()).isNull();
            assertThat(n.getParams()).containsEntry("owner", "Nguyễn Văn An");
        });
    }
}
