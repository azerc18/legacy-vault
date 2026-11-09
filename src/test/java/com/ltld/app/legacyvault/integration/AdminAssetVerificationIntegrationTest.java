package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminUserDetailResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminInsightService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** B.4: ngăn kéo Admin có trạng thái xác thực tài sản nhưng không lộ tên tài sản. */
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
class AdminAssetVerificationIntegrationTest extends MySqlTestContainer {


    @MockitoBean private EmailSender emailSender;

    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private DigitalAssetRepository digitalAssetRepository;
    @Autowired private AdminInsightService adminInsightService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM digital_assets");
        jdbcTemplate.update("DELETE FROM vaults");
        for (UUID id : users) {
            jdbcTemplate.update("DELETE FROM users WHERE BIN_TO_UUID(id) = ?", id.toString());
        }
        users.clear();
    }

    private User user(String prefix) {
        User u = userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com").fullName(prefix)
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        users.add(u.getId());
        return u;
    }

    private Vault vault(User owner, VaultStatus status, LocalDateTime deadline) {
        return vaultRepository.save(Vault.builder().owner(owner).name("V").status(status)
                .claimDeadlineAt(deadline).build());
    }

    @Test
    void adminUserDetail_listsAssetVerificationWithoutAssetName() {
        User owner = user("owner");
        Vault v = vault(owner, VaultStatus.ACTIVE, null);
        digitalAssetRepository.save(DigitalAsset.builder().vault(v).assetType(AssetType.EMAIL).assetName("private-name")
                .encryptedSecret("x").encryptionKeyRef("k").build());

        AdminUserDetailResponse detail = adminInsightService.getUserDetail(owner.getId());

        assertThat(detail.getAssetVerifications()).singleElement().satisfies(a -> {
            assertThat(a.getAssetType()).isEqualTo("EMAIL");
            assertThat(a.getVaultName()).isEqualTo("V");
            assertThat(a.getStatus()).isEqualTo("SELF_DECLARED");
        });
        assertThat(detail.getAssetVerifications().toString()).doesNotContain("private-name");
    }
}
