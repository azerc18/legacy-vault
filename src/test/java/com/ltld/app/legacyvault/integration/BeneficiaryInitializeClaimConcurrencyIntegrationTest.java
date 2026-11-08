package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
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

// #5: hai request khởi tạo claim cùng lúc (chưa có claim) -> không lỗi unique, cùng dùng một claim
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
class BeneficiaryInitializeClaimConcurrencyIntegrationTest extends MySqlTestContainer {

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
    private User beneficiary;

    @BeforeEach
    void setUp() {
        User owner = newUser("owner@example.com");
        beneficiary = newUser("ben@example.com");
        vault = vaultRepository.save(Vault.builder().owner(owner).name("V").status(VaultStatus.UNLOCKED)
                .claimDeadlineAt(LocalDateTime.now().plusDays(30)).build());
        vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(vault).user(beneficiary)
                .email(beneficiary.getEmail()).status(BeneficiaryInviteStatus.INVITED).build());
    }

    @AfterEach
    void cleanUp() {
        verificationRepository.deleteAll();
        claimRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    @RepeatedTest(5)
    void twoInitializeClaimAtSameTime_bothSucceedWithOneClaim() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BeneficiaryClaimResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                BeneficiaryClaimRequest request = new BeneficiaryClaimRequest();
                request.setVaultId(vault.getId());
                request.setVerificationMethod(VerificationMethod.OTP);
                return beneficiaryService.initializeClaim(request, beneficiary.getId());
            }));
        }
        start.countDown();
        List<BeneficiaryClaimResponse> responses = new ArrayList<>();
        for (Future<BeneficiaryClaimResponse> f : futures) {
            responses.add(f.get(30, TimeUnit.SECONDS));   // trước khi sửa: 1 request ném DataIntegrityViolationException
        }
        pool.shutdown();

        assertThat(claimRepository.findAllByVaultId(vault.getId())).hasSize(1);
        assertThat(responses).extracting(BeneficiaryClaimResponse::getClaimId).containsOnly(responses.get(0).getClaimId());
    }

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Tester").passwordHash("x").status(UserStatus.ACTIVE).build());
    }
}
