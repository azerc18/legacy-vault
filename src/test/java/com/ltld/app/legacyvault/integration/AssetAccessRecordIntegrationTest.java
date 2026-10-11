package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.AssetAccessRecordRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm chứng trên MySQL thật: INSERT IGNORE bind UUID đúng, ghi trùng không lỗi, findUnaccessedAssets lọc đúng.
// Mỗi test chạy trong một transaction và tự rollback, nên không cần dọn dữ liệu.
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AssetAccessRecordIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private DigitalAssetRepository assetRepository;
    @Autowired private BeneficiaryClaimRepository claimRepository;
    @Autowired private AssetAccessRecordRepository accessRepository;

    private User owner;
    private User beneficiary;
    private Vault vault;
    private BeneficiaryClaim claim;
    private DigitalAsset a1;
    private DigitalAsset a2;

    @BeforeEach
    void setUp() {
        owner = newUser("owner@example.com");
        beneficiary = newUser("ben@example.com");
        vault = newVault();
        claim = newClaim(vault);
        a1 = newAsset(vault, "A1", AssetStatus.ACTIVE);
        a2 = newAsset(vault, "A2", AssetStatus.ACTIVE);
    }

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Tester").passwordHash("x").status(UserStatus.ACTIVE).build());
    }

    private Vault newVault() {
        return vaultRepository.save(Vault.builder()
                .owner(owner).beneficiary(beneficiary).name("V").status(VaultStatus.UNLOCKED).build());
    }

    private BeneficiaryClaim newClaim(Vault v) {
        return claimRepository.save(BeneficiaryClaim.builder()
                .vault(v).beneficiary(beneficiary).status(ClaimStatus.PENDING)
                .claimDeadlineAt(LocalDateTime.now().plusDays(30)).build());
    }

    private DigitalAsset newAsset(Vault v, String name, AssetStatus status) {
        return assetRepository.save(DigitalAsset.builder()
                .vault(v).assetType(AssetType.OTHER).assetName(name)
                .encryptedSecret("enc").encryptionKeyRef("k").status(status).build());
    }

    @Test
    void insertIfAbsent_samePairTwice_keepsOneRowAndSecondCallDoesNotFail() {
        int first = accessRepository.insertIfAbsent(UUID.randomUUID(), claim.getId(), a1.getId(), LocalDateTime.now());
        int second = accessRepository.insertIfAbsent(UUID.randomUUID(), claim.getId(), a1.getId(), LocalDateTime.now());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();            // bị bỏ qua, không ném lỗi
        assertThat(accessRepository.count()).isEqualTo(1);
    }

    @Test
    void findUnaccessedAssets_excludesAccessedAndRevoked() {
        newAsset(vault, "Revoked", AssetStatus.REVOKED);
        accessRepository.insertIfAbsent(UUID.randomUUID(), claim.getId(), a1.getId(), LocalDateTime.now());

        List<DigitalAsset> result = assetRepository.findUnaccessedAssets(vault.getId(), claim.getId());

        assertThat(result).extracting(DigitalAsset::getAssetName).containsExactly("A2");
    }

    @Test
    void findUnaccessedAssets_doesNotMixOtherClaimOrVault() {
        // Vault khác có claim riêng và đã xem tài sản của nó: không được ảnh hưởng kết quả của vault này
        Vault otherVault = newVault();
        BeneficiaryClaim otherClaim = newClaim(otherVault);
        DigitalAsset otherAsset = newAsset(otherVault, "Other", AssetStatus.ACTIVE);
        accessRepository.insertIfAbsent(UUID.randomUUID(), otherClaim.getId(), otherAsset.getId(), LocalDateTime.now());

        List<DigitalAsset> result = assetRepository.findUnaccessedAssets(vault.getId(), claim.getId());

        assertThat(result).extracting(DigitalAsset::getAssetName).containsExactlyInAnyOrder("A1", "A2");
    }
}