package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
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

// FR-10: kiểm câu JPQL findProgressRows trên MySQL thật.
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false",
        "app.beneficiary.notify-retry.enabled=false",
        "app.beneficiary.vault-purge.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ExecutorHandoverRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired private VerificationRequestVaultRepository verificationRequestVaultRepository;
    @Autowired private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private UserRepository userRepository;

    private final List<UUID> linkIds = new ArrayList<>();
    private final List<UUID> requestIds = new ArrayList<>();
    private final List<UUID> vaultIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        verificationRequestVaultRepository.deleteAllById(linkIds);
        legalVerificationRequestRepository.deleteAllById(requestIds);
        vaultRepository.deleteAllById(vaultIds);
        userRepository.deleteAllById(userIds);
        linkIds.clear();
        requestIds.clear();
        vaultIds.clear();
        userIds.clear();
    }

    @Test
    void findProgressRows_returnsOnlyThisExecutorsRequestsForThisOwner_withEntitiesLoaded() {
        User owner = user("owner");
        User otherOwner = user("other-owner");
        User executor = user("executor");
        User otherExecutor = user("other-executor");
        Vault mine = vault(owner, "Mine");
        Vault ofOtherExecutor = vault(owner, "Other executor's");
        Vault ofOtherOwner = vault(otherOwner, "Other owner's");

        link(request(owner, executor, LegalVerificationStatus.PENDING), mine);
        link(request(owner, otherExecutor, LegalVerificationStatus.PENDING), ofOtherExecutor);
        link(request(otherOwner, executor, LegalVerificationStatus.PENDING), ofOtherOwner);

        List<VerificationRequestVault> rows =
                verificationRequestVaultRepository.findProgressRows(executor.getId(), owner.getId());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getVault().getName()).isEqualTo("Mine");
        assertThat(rows.get(0).getRequest().getStatus()).isEqualTo(LegalVerificationStatus.PENDING);
    }

    @Test
    void findProgressRows_listsNewestRequestFirst_andKeepsEveryRequestOfAVault() throws Exception {
        User owner = user("owner");
        User executor = user("executor");
        Vault vault = vault(owner, "Resubmitted");

        link(request(owner, executor, LegalVerificationStatus.REJECTED), vault);
        Thread.sleep(20);
        LegalVerificationRequest newest = request(owner, executor, LegalVerificationStatus.PENDING);
        link(newest, vault);

        List<VerificationRequestVault> rows =
                verificationRequestVaultRepository.findProgressRows(executor.getId(), owner.getId());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRequest().getId()).isEqualTo(newest.getId());
        assertThat(rows.get(0).getRequest().getStatus()).isEqualTo(LegalVerificationStatus.PENDING);
        assertThat(rows.get(1).getRequest().getStatus()).isEqualTo(LegalVerificationStatus.REJECTED);
    }

    @Test
    void findProgressRows_executorWithoutRequests_returnsEmptyList() {
        User owner = user("owner");
        User executor = user("executor");
        vault(owner, "Untouched");

        assertThat(verificationRequestVaultRepository.findProgressRows(executor.getId(), owner.getId())).isEmpty();
    }

    private User user(String name) {
        User saved = userRepository.save(User.builder()
                .email(name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(saved.getId());
        return saved;
    }

    private Vault vault(User owner, String name) {
        Vault saved = vaultRepository.save(Vault.builder()
                .owner(owner)
                .name(name)
                .status(VaultStatus.ACTIVE)
                .build());
        vaultIds.add(saved.getId());
        return saved;
    }

    private LegalVerificationRequest request(User owner, User executor, LegalVerificationStatus status) {
        LegalVerificationRequest saved = legalVerificationRequestRepository.save(LegalVerificationRequest.builder()
                .owner(owner)
                .executor(executor)
                .deathCertificateFileUrlEncrypted("/files/cert.pdf")
                .status(status)
                .build());
        requestIds.add(saved.getId());
        return saved;
    }

    private void link(LegalVerificationRequest request, Vault vault) {
        VerificationRequestVault saved = verificationRequestVaultRepository.save(VerificationRequestVault.builder()
                .request(request)
                .vault(vault)
                .build());
        linkIds.add(saved.getId());
    }
}
