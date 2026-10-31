package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.AssetAccessRecordRepository;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Kiểm chứng trên MySQL thật: INSERT IGNORE bind UUID đúng, ghi trùng không lỗi, findUnaccessedAssets lọc đúng.
// Mỗi test chạy trong một transaction và tự rollback, nên không cần dọn dữ liệu.
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false"
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
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private AssetAllocationRepository allocationRepository;

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

    // N2a: cùng vault khác email -> lưu được; trùng (vault, email) -> vi phạm unique
    @Test
    void vaultBeneficiaries_uniquePerVaultAndEmail() {
        vaultBeneficiaryRepository.saveAndFlush(VaultBeneficiary.builder()
                .vault(vault).user(beneficiary).email("ben@example.com")
                .status(BeneficiaryInviteStatus.INVITED).build());
        vaultBeneficiaryRepository.saveAndFlush(VaultBeneficiary.builder()
                .vault(vault).email("ben2@example.com").build());   // status mặc định PENDING_REGISTRATION

        // Kiểm tra truy vấn TRƯỚC khi gây lỗi
        assertThat(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vault.getId())).hasSize(2);
        // Khớp không phân biệt hoa thường nhờ collation của cột email (utf8mb4_unicode_ci), không phải do code chuẩn hoá
        assertThat(vaultBeneficiaryRepository.findPendingByEmail("BEN2@example.com")).hasSize(1);
        assertThat(vaultBeneficiaryRepository.existsByOwnerIdAndEmail(owner.getId(), "ben@example.com")).isTrue();

        assertThrows(DataIntegrityViolationException.class,
                () -> vaultBeneficiaryRepository.saveAndFlush(VaultBeneficiary.builder()
                        .vault(vault).email("ben@example.com").build()));
    }

    // N2a: mỗi (tài sản, người nhận) một dòng; mặc định EXCLUSIVE 100%; trùng -> vi phạm unique
    @Test
    void assetAllocations_uniquePerAssetAndBeneficiary() {
        DigitalAsset asset = newAsset(vault, "A", AssetStatus.ACTIVE);
        VaultBeneficiary vb = vaultBeneficiaryRepository.saveAndFlush(VaultBeneficiary.builder()
                .vault(vault).user(beneficiary).email("ben@example.com").build());
        AssetAllocation saved = allocationRepository.saveAndFlush(
                AssetAllocation.builder().asset(asset).beneficiary(vb).build());

        assertThat(allocationRepository.findById(saved.getId()).orElseThrow().getSharePercent())
                .isEqualByComparingTo("100.00");
        assertThat(allocationRepository.findByAssetId(asset.getId())).hasSize(1);

        assertThrows(DataIntegrityViolationException.class,
                () -> allocationRepository.saveAndFlush(
                        AssetAllocation.builder().asset(asset).beneficiary(vb).build()));
    }

    // N2a: xoá phân bổ theo vault (con trước, cha sau)
    @Test
    void assetAllocations_deleteByVaultId_removesAllOfVault() {
        DigitalAsset asset = newAsset(vault, "A", AssetStatus.ACTIVE);
        VaultBeneficiary vb = vaultBeneficiaryRepository.saveAndFlush(VaultBeneficiary.builder()
                .vault(vault).user(beneficiary).email("ben@example.com").build());
        allocationRepository.saveAndFlush(AssetAllocation.builder().asset(asset).beneficiary(vb).build());

        assertThat(allocationRepository.deleteByVaultId(vault.getId())).isEqualTo(1);
        assertThat(allocationRepository.findByAssetId(asset.getId())).isEmpty();
    }
}