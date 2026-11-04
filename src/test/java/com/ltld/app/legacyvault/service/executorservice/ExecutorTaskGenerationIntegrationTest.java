package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorTask;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.integration.MySqlTestContainer;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorTaskRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F5: sinh checklist trên MySQL thật - chỉ tài sản EXECUTOR_DISPOSITION có di nguyện, gọi lại không sinh trùng.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
class ExecutorTaskGenerationIntegrationTest extends MySqlTestContainer {

    @Autowired private ExecutorTaskService executorTaskService;
    @Autowired private ExecutorTaskRepository executorTaskRepository;
    @Autowired private DigitalAssetRepository digitalAssetRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;

    private UUID ownerId;
    private UUID vaultId;

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> {
            executorTaskRepository.deleteAll(executorTaskRepository.findByVaultIds(List.of(vaultId)));
            digitalAssetRepository.deleteAll(digitalAssetRepository.findByVaultId(vaultId));
        });
        vaultRepository.deleteById(vaultId);
        userRepository.deleteById(ownerId);
    }

    @Test
    void generate_onlyDispositionAssetsWithWish_andIsIdempotent() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(User.builder().email("owner-" + tag + "@example.com").fullName("Owner")
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        ownerId = owner.getId();
        Vault vault = vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag)
                .status(VaultStatus.UNLOCKED).build());
        vaultId = vault.getId();
        asset(vault, AssetType.SOCIAL_MEDIA, TransferMechanism.EXECUTOR_DISPOSITION,
                "{\"platform\":\"Facebook\",\"usernameOrHandle\":\"owner.fb\",\"wish\":\"MEMORIALIZE\"}");
        asset(vault, AssetType.EMAIL, TransferMechanism.EXECUTOR_DISPOSITION,
                "{\"platform\":\"Gmail\",\"usernameOrHandle\":\"owner@gmail.com\",\"wish\":\"TRANSFER_DATA\"}");
        asset(vault, AssetType.BANK_ACCOUNT, TransferMechanism.LEGAL_CLAIM_DOSSIER, "{\"institutionName\":\"VCB\"}");

        Integer first = tx.execute(s -> executorTaskService.generateForVaults(List.of(vaultId)));
        Integer second = tx.execute(s -> executorTaskService.generateForVaults(List.of(vaultId)));

        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        List<ExecutorTask> tasks = tx.execute(s -> executorTaskRepository.findByVaultIds(List.of(vaultId)));
        assertThat(tasks).extracting(ExecutorTask::getWish)
                .containsExactlyInAnyOrder(ExecutorTaskWish.MEMORIALIZE, ExecutorTaskWish.TRANSFER_DATA);
    }

    private void asset(Vault vault, AssetType type, TransferMechanism mechanism, String publicJson) {
        digitalAssetRepository.save(DigitalAsset.builder().vault(vault).assetType(type).assetName(type.name())
                .encryptedSecret("cipher").encryptionKeyRef("test").transferMechanism(mechanism)
                .publicFieldsJson(publicJson).build());
    }
}
