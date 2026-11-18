package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.SigningKey;
import com.ltld.app.legacyvault.repository.SigningKeyRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.legalverificationservice.SigningKeyService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3: khóa ký sinh lần đầu trong transaction riêng, dùng được ngay trong transaction đang mở,
 * khóa riêng lưu đã mã hóa, và 2 lệnh ký đầu tiên chạy cùng lúc vẫn chỉ ra 1 khóa.
 */
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
class SigningKeyIntegrationTest extends MySqlTestContainer {

    @MockitoBean private EmailSender emailSender;

    @Autowired private SigningKeyService signingKeyService;
    @Autowired private SigningKeyRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CryptoService cryptoService;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void firstKey_createdInsideOpenTransaction_signsAndVerifies_privateKeyEncrypted() throws Exception {
        String sig = new TransactionTemplate(transactionManager).execute(status -> {
            repository.count(); // giống lệnh ký: đã đọc DB trước khi cần khóa
            SigningKey key = signingKeyService.getOrCreateActiveKey();
            return signingKeyService.sign(key, "payload");
        });

        List<SigningKey> keys = repository.findAll();
        assertThat(keys).hasSize(1);
        SigningKey key = keys.get(0);
        assertThat(key.getKeyRef()).isEqualTo("lv-sign-v1");
        // Bản lưu DB là bản đã mã hóa; giải mã ra mới là khóa PKCS#8 đọc được
        String pkcs8 = cryptoService.decrypt(key.getPrivateKeyEncrypted());
        assertThat(pkcs8).isNotEqualTo(key.getPrivateKeyEncrypted());
        assertThat(java.security.KeyFactory.getInstance("EC").generatePrivate(
                new java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(pkcs8)))).isNotNull();
        assertThat(signingKeyService.verify(key.getPublicKeyPem(), "payload", sig)).isTrue();
        assertThat(signingKeyService.getOrCreateActiveKey().getId()).isEqualTo(key.getId());
    }

    @Test
    void concurrentFirstSigners_endUpWithOneKey() throws Exception {
        assertConcurrentFirstSigners(false);
    }

    // Review #293 m1: giống lệnh ký thật, mỗi luồng đã mở transaction và đọc DB (snapshot REPEATABLE READ)
    // TRƯỚC khi bên thắng commit khóa. Bên thua phải đọc lại trong transaction mới mới thấy khóa đó.
    @Test
    void concurrentFirstSigners_insideOpenTransactions_endUpWithOneKey() throws Exception {
        assertConcurrentFirstSigners(true);
    }

    private void assertConcurrentFirstSigners(boolean insideTransaction) throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch snapshotsOpen = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SigningKey>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                if (!insideTransaction) {
                    snapshotsOpen.countDown();
                    start.await();
                    return signingKeyService.getOrCreateActiveKey();
                }
                return new TransactionTemplate(transactionManager).execute(status -> {
                    repository.count(); // mở snapshot trước khi ai đó tạo khóa
                    snapshotsOpen.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    SigningKey key = signingKeyService.getOrCreateActiveKey();
                    signingKeyService.sign(key, "payload"); // khóa nhận được dùng ký được ngay
                    return key;
                });
            }));
        }
        assertThat(snapshotsOpen.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<SigningKey> f : futures) {
            assertThat(f.get(60, TimeUnit.SECONDS).getKeyRef()).isEqualTo("lv-sign-v1");
        }
        pool.shutdown();
        assertThat(repository.count()).isEqualTo(1);
    }
}
