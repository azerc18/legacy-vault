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
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F5: sinh checklist trên MySQL thật - chỉ tài sản EXECUTOR_DISPOSITION có di nguyện, gọi lại / gọi song song
 * không sinh trùng và không lỗi (unique uk_executor_task_asset).
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
    private DigitalAsset facebook;

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
        givenVaultWithAssets();

        int first = executorTaskService.generateForVaults(List.of(vaultId));
        int second = executorTaskService.generateForVaults(List.of(vaultId));

        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        assertThat(tasks()).extracting(ExecutorTask::getWish)
                .containsExactlyInAnyOrder(ExecutorTaskWish.MEMORIALIZE, ExecutorTaskWish.TRANSFER_DATA);
    }

    // Review F5 #1: 2 request cùng sinh (React StrictMode gửi 2 GET, hoặc trùng listener) -> không lỗi, mỗi tài sản 1 task
    @Test
    void generate_twiceInParallel_noErrorAndOneTaskPerAsset() throws Exception {
        givenVaultWithAssets();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> {
                start.await();
                return executorTaskService.generateForVaults(List.of(vaultId));
            });
            Future<Integer> b = pool.submit(() -> {
                start.await();
                return executorTaskService.generateForVaults(List.of(vaultId));
            });
            start.countDown();

            // get() ném lại exception nếu một bên lỗi (trước khi sửa: DataIntegrityViolationException)
            int created = a.get(30, TimeUnit.SECONDS) + b.get(30, TimeUnit.SECONDS);
            assertThat(created).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
        assertThat(tasks()).hasSize(2);
    }

    // Nhánh trùng chạy chắc chắn (không phụ thuộc thời điểm): ghi lần 2 cho cùng tài sản trả false, không ném lỗi
    @Test
    void insertIgnoringDuplicate_sameAssetTwice_secondIsSkipped() {
        givenVaultWithAssets();
        ExecutorTaskServiceImpl target = AopTestUtils.getTargetObject(executorTaskService);
        Vault vault = vaultRepository.findById(vaultId).orElseThrow();

        boolean first = target.insertIgnoringDuplicate(task(vault, facebook));
        boolean second = target.insertIgnoringDuplicate(task(vault, facebook));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(tasks()).hasSize(1);
    }

    private void givenVaultWithAssets() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(User.builder().email("owner-" + tag + "@example.com").fullName("Owner")
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        ownerId = owner.getId();
        Vault vault = vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag)
                .status(VaultStatus.UNLOCKED).build());
        vaultId = vault.getId();
        facebook = asset(vault, AssetType.SOCIAL_MEDIA, TransferMechanism.EXECUTOR_DISPOSITION,
                "{\"platform\":\"Facebook\",\"usernameOrHandle\":\"owner.fb\",\"wish\":\"MEMORIALIZE\"}");
        asset(vault, AssetType.EMAIL, TransferMechanism.EXECUTOR_DISPOSITION,
                "{\"platform\":\"Gmail\",\"usernameOrHandle\":\"owner@gmail.com\",\"wish\":\"TRANSFER_DATA\"}");
        asset(vault, AssetType.BANK_ACCOUNT, TransferMechanism.LEGAL_CLAIM_DOSSIER, "{\"institutionName\":\"VCB\"}");
    }

    private List<ExecutorTask> tasks() {
        return tx.execute(s -> executorTaskRepository.findByVaultIds(List.of(vaultId)));
    }

    private static ExecutorTask task(Vault vault, DigitalAsset asset) {
        return ExecutorTask.builder().vault(vault).asset(asset).platform("Facebook").wish(ExecutorTaskWish.MEMORIALIZE)
                .build();
    }

    private DigitalAsset asset(Vault vault, AssetType type, TransferMechanism mechanism, String publicJson) {
        return digitalAssetRepository.save(DigitalAsset.builder().vault(vault).assetType(type).assetName(type.name())
                .encryptedSecret("cipher").encryptionKeyRef("test").transferMechanism(mechanism)
                .publicFieldsJson(publicJson).build());
    }
}
