package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultRequest;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// N2b: hai người thụ hưởng cùng đóng phần của mình đồng thời -> vault phải sang CLAIMED (không bị bỏ sót)
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
class BeneficiaryCloseVaultConcurrencyIntegrationTest extends MySqlTestContainer{

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private BeneficiaryService beneficiaryService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private BeneficiaryClaimRepository claimRepository;
    @Autowired private IdentityVerificationRepository verificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Vault vault;
    private User benA;
    private User benB;

    @BeforeEach
    void setUp() {
        User owner = newUser("owner@example.com");
        benA = newUser("a@example.com");
        benB = newUser("b@example.com");
        vault = vaultRepository.save(Vault.builder().owner(owner).name("V").status(VaultStatus.UNLOCKED)
                .claimDeadlineAt(LocalDateTime.now().plusDays(30)).build());
        for (User u : List.of(benA, benB)) {
            vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(vault).user(u).email(u.getEmail())
                    .status(BeneficiaryInviteStatus.ACCEPTED).build());
            claimRepository.save(BeneficiaryClaim.builder().vault(vault).beneficiary(u)
                    .status(ClaimStatus.PENDING).claimDeadlineAt(LocalDateTime.now().plusDays(30)).build());
            verificationRepository.save(IdentityVerification.builder().vault(vault).beneficiary(u)
                    .method(VerificationMethod.OTP).status(VerificationStatus.SUCCESS)
                    .verifiedAt(LocalDateTime.now()).createdAt(LocalDateTime.now()).build());
        }
    }

    @AfterEach
    void cleanUp() {
        verificationRepository.deleteAll();
        claimRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM asset_allocations");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    @RepeatedTest(10)
    void twoBeneficiariesCloseAtSameTime_vaultEndsClaimed() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (User u : List.of(benA, benB)) {
            futures.add(pool.submit(() -> {
                start.await();
                CloseVaultRequest request = new CloseVaultRequest();
                request.setConfirmed(true);
                return beneficiaryService.closeVault(vault.getId(), request, u.getId());
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(vaultRepository.findById(vault.getId()).orElseThrow().getStatus()).isEqualTo(VaultStatus.CLAIMED);
        assertThat(claimRepository.findAllByVaultId(vault.getId()))
                .hasSize(2).allMatch(c -> c.getStatus() == ClaimStatus.CLAIMED);
    }

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Tester").passwordHash("x").status(UserStatus.ACTIVE).build());
    }
}
