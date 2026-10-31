package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
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

// N2-verifier: verifier là beneficiary của BẤT KỲ vault nào trong hồ sơ thì không thấy / không đếm hồ sơ đó (MySQL thật).
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
class VerifierBeneficiaryConflictIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired private VaultBeneficiaryRepository beneficiaryRepository;
    @Autowired private VerificationRequestVaultRepository requestVaultRepository;
    @Autowired private LegalVerificationRequestRepository requestRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private UserRepository userRepository;

    private final List<UUID> beneficiaryIds = new ArrayList<>();
    private final List<UUID> linkIds = new ArrayList<>();
    private final List<UUID> requestIds = new ArrayList<>();
    private final List<UUID> vaultIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        beneficiaryRepository.deleteAllById(beneficiaryIds);
        requestVaultRepository.deleteAllById(linkIds);
        requestRepository.deleteAllById(requestIds);
        vaultRepository.deleteAllById(vaultIds);
        userRepository.deleteAllById(userIds);
        beneficiaryIds.clear();
        linkIds.clear();
        requestIds.clear();
        vaultIds.clear();
        userIds.clear();
    }

    @Test
    void verifierIsSecondBeneficiaryByAccount_requestIsHiddenAndNotCounted() {
        User owner = user("owner");
        User executor = user("executor");
        User verifier = user("verifier");
        Vault vault = vault(owner);
        beneficiary(vault, "first@example.com", null);
        beneficiary(vault, verifier.getEmail(), verifier);
        LegalVerificationRequest request = request(owner, executor, vault);

        assertThat(visibleIds(verifier)).doesNotContain(request.getId());
        assertThat(requestRepository.countPendingVisibleTo(LegalVerificationStatus.PENDING, verifier.getId()))
                .isZero();
        assertThat(beneficiaryRepository.isBeneficiaryOfRequest(request.getId(), verifier.getId())).isTrue();
    }

    @Test
    void verifierIsBeneficiaryByEmailOnly_requestIsStillHidden() {
        User owner = user("owner");
        User executor = user("executor");
        User verifier = user("verifier");
        Vault vault = vault(owner);
        beneficiary(vault, verifier.getEmail().toUpperCase(), null); // chưa gắn tài khoản, khác hoa thường
        LegalVerificationRequest request = request(owner, executor, vault);

        assertThat(visibleIds(verifier)).doesNotContain(request.getId());
        assertThat(beneficiaryRepository.isBeneficiaryOfRequest(request.getId(), verifier.getId())).isTrue();
    }

    @Test
    void verifierIsBeneficiaryOfOnlyOneOfTwoVaults_requestIsStillHidden() {
        User owner = user("owner");
        User executor = user("executor");
        User verifier = user("verifier");
        Vault vaultA = vault(owner);
        Vault vaultB = vault(owner);
        beneficiary(vaultA, "someone@example.com", null);
        beneficiary(vaultB, verifier.getEmail(), verifier);
        LegalVerificationRequest request = request(owner, executor, vaultA);
        link(request, vaultB);

        assertThat(visibleIds(verifier)).doesNotContain(request.getId());
    }

    @Test
    void unrelatedVerifier_stillSeesAndCountsTheRequest() {
        User owner = user("owner");
        User executor = user("executor");
        User verifier = user("verifier");
        User otherBeneficiary = user("beneficiary");
        Vault vault = vault(owner);
        beneficiary(vault, otherBeneficiary.getEmail(), otherBeneficiary);
        LegalVerificationRequest request = request(owner, executor, vault);

        assertThat(visibleIds(verifier)).contains(request.getId());
        assertThat(requestRepository.countPendingVisibleTo(LegalVerificationStatus.PENDING, verifier.getId()))
                .isGreaterThanOrEqualTo(1);
        assertThat(beneficiaryRepository.isBeneficiaryOfRequest(request.getId(), verifier.getId())).isFalse();
    }

    private List<UUID> visibleIds(User verifier) {
        return requestRepository.findPendingVisibleTo(LegalVerificationStatus.PENDING, verifier.getId())
                .stream().map(LegalVerificationRequest::getId).toList();
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

    private Vault vault(User owner) {
        Vault saved = vaultRepository.save(Vault.builder()
                .owner(owner)
                .name("Vault " + UUID.randomUUID().toString().substring(0, 4))
                .status(VaultStatus.ACTIVE)
                .build());
        vaultIds.add(saved.getId());
        return saved;
    }

    private void beneficiary(Vault vault, String email, User user) {
        VaultBeneficiary saved = beneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(vault)
                .email(email)
                .user(user)
                .build());
        beneficiaryIds.add(saved.getId());
    }

    private LegalVerificationRequest request(User owner, User executor, Vault vault) {
        LegalVerificationRequest saved = requestRepository.save(LegalVerificationRequest.builder()
                .owner(owner)
                .executor(executor)
                .deathCertificateFileUrlEncrypted("/files/cert.pdf")
                .status(LegalVerificationStatus.PENDING)
                .build());
        requestIds.add(saved.getId());
        link(saved, vault);
        return saved;
    }

    private void link(LegalVerificationRequest request, Vault vault) {
        VerificationRequestVault saved = requestVaultRepository.save(VerificationRequestVault.builder()
                .request(request)
                .vault(vault)
                .build());
        linkIds.add(saved.getId());
    }
}
