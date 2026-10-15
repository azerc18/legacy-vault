package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// FR-08: kiểm hai câu JPQL của màn hình tài sản được ủy quyền trên MySQL thật.
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ExecutorAssetRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired private VaultRepository vaultRepository;
    @Autowired private DigitalAssetRepository digitalAssetRepository;
    @Autowired private UserRepository userRepository;

    private final List<UUID> assetIds = new ArrayList<>();
    private final List<UUID> vaultIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        digitalAssetRepository.deleteAllById(assetIds);
        vaultRepository.deleteAllById(vaultIds);
        userRepository.deleteAllById(userIds);
        assetIds.clear();
        vaultIds.clear();
        userIds.clear();
    }

    @Test
    void findByOwnerIdWithBeneficiary_returnsOwnVaultsInCreationOrder_withBeneficiaryLoaded() throws Exception {
        User owner = user("owner");
        User otherOwner = user("other-owner");
        User beneficiary = user("beneficiary");

        Vault first = vault(owner, "First", beneficiary);
        Thread.sleep(20);
        Vault second = vault(owner, "Second", null);
        vault(otherOwner, "Not mine", null);

        List<Vault> result = vaultRepository.findByOwnerIdWithBeneficiary(owner.getId());

        assertThat(result).extracting(Vault::getId).containsExactly(first.getId(), second.getId());
        assertThat(result.get(0).getBeneficiary().getFullName()).isEqualTo(beneficiary.getFullName());
        assertThat(result.get(1).getBeneficiary()).isNull();
    }

    @Test
    void findByOwnerIdWithBeneficiary_ownerWithoutVaults_returnsEmptyList() {
        User owner = user("lonely-owner");

        assertThat(vaultRepository.findByOwnerIdWithBeneficiary(owner.getId())).isEmpty();
    }

    @Test
    void findByVaultIdInAndStatus_returnsOnlyActiveAssetsOfRequestedVaults() {
        User owner = user("owner");
        Vault vault = vault(owner, "Vault", null);
        Vault notRequested = vault(owner, "Not requested", null);

        DigitalAsset active = asset(vault, "Active asset", AssetStatus.ACTIVE);
        asset(vault, "Revoked asset", AssetStatus.REVOKED);
        asset(notRequested, "Other vault asset", AssetStatus.ACTIVE);

        List<DigitalAsset> result = digitalAssetRepository.findByVaultIdInAndStatus(
                List.of(vault.getId()), AssetStatus.ACTIVE);

        assertThat(result).extracting(DigitalAsset::getId).containsExactly(active.getId());
    }

    @Test
    void findByVaultIdInAndStatus_groupsAssetsOfSeveralVaultsInOneCall() {
        User owner = user("owner");
        Vault a = vault(owner, "A", null);
        Vault b = vault(owner, "B", null);
        DigitalAsset assetA = asset(a, "Asset A", AssetStatus.ACTIVE);
        DigitalAsset assetB = asset(b, "Asset B", AssetStatus.ACTIVE);

        List<DigitalAsset> result = digitalAssetRepository.findByVaultIdInAndStatus(
                List.of(a.getId(), b.getId()), AssetStatus.ACTIVE);

        assertThat(result).extracting(DigitalAsset::getId).containsExactlyInAnyOrder(assetA.getId(), assetB.getId());
    }

    private User user(String name) {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        User saved = userRepository.save(User.builder()
                .email(email)
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(saved.getId());
        return saved;
    }

    private Vault vault(User owner, String name, User beneficiary) {
        Vault saved = vaultRepository.save(Vault.builder()
                .owner(owner)
                .beneficiary(beneficiary)
                .name(name)
                .status(VaultStatus.ACTIVE)
                .build());
        vaultIds.add(saved.getId());
        return saved;
    }

    private DigitalAsset asset(Vault vault, String name, AssetStatus status) {
        DigitalAsset saved = digitalAssetRepository.save(DigitalAsset.builder()
                .vault(vault)
                .assetType(AssetType.OTHER)
                .assetName(name)
                .encryptedSecret("cipher")
                .encryptionKeyRef("key-ref")
                .status(status)
                .build());
        assetIds.add(saved.getId());
        return saved;
    }
}
