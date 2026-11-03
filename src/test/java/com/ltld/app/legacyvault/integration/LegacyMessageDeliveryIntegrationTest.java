package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.LegacyMessage;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.messageservice.LegacyMessageService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false",
        "app.beneficiary.claim-expiry.enabled=false",
        "app.legacy-message.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class LegacyMessageDeliveryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private LegacyMessageRepository legacyMessageRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private LegacyMessageService legacyMessageService;

    @Test
    @DisplayName("M1: deliverOnClaimMessages trên MySQL thực tế load entity trong REQUIRES_NEW không bị LazyInitializationException")
    void deliverOnClaimMessages_RealDatabase_DeliversAndCreatesNotification() {
        // 1. Tạo Owner
        User owner = userRepository.save(User.builder()
                .email("owner-" + UUID.randomUUID() + "@test.com")
                .fullName("Chu So Huu Ket")
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());

        // 2. Tạo Beneficiary user
        User benUser = userRepository.save(User.builder()
                .email("ben-" + UUID.randomUUID() + "@test.com")
                .fullName("Nguoi Thu Huong")
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());

        // 3. Tạo Vault với trạng thái UNLOCKED
        Vault vault = vaultRepository.save(Vault.builder()
                .owner(owner)
                .name("Integration Test Vault")
                .status(VaultStatus.UNLOCKED)
                .build());

        // 4. Tạo VaultBeneficiary liên kết với Vault và User
        vaultBeneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(vault)
                .user(benUser)
                .email(benUser.getEmail())
                .fullName(benUser.getFullName())
                .status(BeneficiaryInviteStatus.INVITED)
                .build());

        // 5. Tạo LegacyMessage với ON_CLAIM và status ACTIVE
        LegacyMessage message = legacyMessageRepository.save(LegacyMessage.builder()
                .vault(vault)
                .recipientType(LegacyMessageRecipientType.ALL_BENEFICIARIES)
                .title("Farewell Letter Real DB")
                .content("enc:secretcontent")
                .deliveryTrigger(LegacyMessageDeliveryTrigger.ON_CLAIM)
                .status(LegacyMessageStatus.ACTIVE)
                .build());

        // 6. Kích hoạt giao lời nhắn
        int delivered = legacyMessageService.deliverOnClaimMessages(vault.getId());

        // 7. Xác nhận kết quả
        assertThat(delivered).isEqualTo(1);

        LegacyMessage reloaded = legacyMessageRepository.findById(message.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LegacyMessageStatus.DELIVERED);
        assertThat(reloaded.getDeliveredAt()).isNotNull();

        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient() != null && n.getRecipient().getId().equals(benUser.getId()))
                .filter(n -> n.getType() == NotificationType.LEGACY_MESSAGE_DELIVERED)
                .toList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getContent()).contains("You have a new legacy message from Chu So Huu Ket in vault \"Integration Test Vault\".");

        verify(emailSender).sendLegacyMessageDeliveredNotification(
                eq(benUser.getEmail()), eq("Nguoi Thu Huong"), eq("Chu So Huu Ket"),
                eq("Integration Test Vault"), eq(vault.getId()), eq(true));
    }
}
