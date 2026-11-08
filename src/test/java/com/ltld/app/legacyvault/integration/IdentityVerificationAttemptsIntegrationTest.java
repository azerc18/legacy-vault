package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.beneficiarydto.SendIdentityOtpRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// #10 / #19: lượt nhập sai OTP của FR-17 phải được lưu thật vào DB (noRollbackFor), và câu SUM lượt thử
// của các phiên PENDING khác chỉ tính đúng phiên cùng vault + beneficiary trong 24h.
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
class IdentityVerificationAttemptsIntegrationTest extends MySqlTestContainer {

    private static final String RIGHT_OTP = "111111";
    private static final String WRONG_OTP = "222222";

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private BeneficiaryService beneficiaryService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private BeneficiaryClaimRepository claimRepository;
    @Autowired private IdentityVerificationRepository verificationRepository;
    @Autowired private OtpHasher otpHasher;
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
        claimRepository.save(BeneficiaryClaim.builder().vault(vault).beneficiary(beneficiary)
                .status(ClaimStatus.PENDING).claimDeadlineAt(LocalDateTime.now().plusDays(30)).build());
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

    @Test
    void wrongOtp_attemptIsPersistedDespiteException() {
        IdentityVerification session = pendingOtpSession();

        BeneficiaryException ex = catchThrowableOfType(BeneficiaryException.class,
                () -> beneficiaryService.verifyIdentity(request(WRONG_OTP), beneficiary.getId()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Rollback thì giá trị này về 0 và giới hạn 5 lần không bao giờ chạm tới
        assertThat(verificationRepository.findById(session.getId()).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    void fiveWrongOtps_sessionIsLockedInDb() {
        IdentityVerification session = pendingOtpSession();

        for (int i = 1; i <= 4; i++) {
            BeneficiaryException ex = catchThrowableOfType(BeneficiaryException.class,
                    () -> beneficiaryService.verifyIdentity(request(WRONG_OTP), beneficiary.getId()));
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        BeneficiaryException locked = catchThrowableOfType(BeneficiaryException.class,
                () -> beneficiaryService.verifyIdentity(request(WRONG_OTP), beneficiary.getId()));

        assertThat(locked.getStatus()).isEqualTo(HttpStatus.LOCKED);
        IdentityVerification reloaded = verificationRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VerificationStatus.FAILED);
        assertThat(reloaded.getAttemptCount()).isEqualTo(5);
        assertThat(reloaded.getOtpCode()).isNull();
    }

    @Test
    void sumAttemptsOfOtherPendingSessions_countsOnlyRecentPendingSessionsOfSameBeneficiary() {
        IdentityVerification current = saveSession(vault, beneficiary, VerificationStatus.PENDING, 1);
        saveSession(vault, beneficiary, VerificationStatus.PENDING, 2);                 // tính
        IdentityVerification old = saveSession(vault, beneficiary, VerificationStatus.PENDING, 3);
        jdbcTemplate.update("UPDATE identity_verifications SET created_at = ? WHERE id = UUID_TO_BIN(?)",
                LocalDateTime.now().minusHours(25), old.getId().toString());            // quá 24h: không tính
        saveSession(vault, beneficiary, VerificationStatus.FAILED, 4);                  // không PENDING: không tính
        User other = newUser("other@example.com");
        saveSession(vault, other, VerificationStatus.PENDING, 5);                       // người khác: không tính

        int sum = verificationRepository.sumAttemptsOfOtherPendingSessions(
                vault.getId(), beneficiary.getId(), current.getId(), LocalDateTime.now().minusHours(24));

        assertThat(sum).isEqualTo(2);
    }

    @Test
    void sumAttemptsOfOtherPendingSessions_noOtherSession_returnsZero() {
        IdentityVerification current = saveSession(vault, beneficiary, VerificationStatus.PENDING, 3);

        assertThat(verificationRepository.sumAttemptsOfOtherPendingSessions(
                vault.getId(), beneficiary.getId(), current.getId(), LocalDateTime.now().minusHours(24))).isZero();
    }

    // #19: bấm "gửi lại" 2 lần cùng lúc -> chỉ 1 mã được gửi, request kia nhận 429
    @RepeatedTest(5)
    void concurrentResend_onlyOneOtpIsSent() throws Exception {
        IdentityVerification session = saveSession(vault, beneficiary, VerificationStatus.PENDING, 0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                SendIdentityOtpRequest request = new SendIdentityOtpRequest();
                request.setVaultId(vault.getId());
                try {
                    beneficiaryService.sendIdentityOtp(request, beneficiary.getId());
                    return HttpStatus.OK;
                } catch (BeneficiaryException e) {
                    return e.getStatus();
                }
            }));
        }
        start.countDown();
        List<HttpStatus> results = new ArrayList<>();
        for (Future<HttpStatus> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.TOO_MANY_REQUESTS);
        verify(emailSender, times(1)).sendEmail(eq("ben@example.com"), anyString());
        assertThat(verificationRepository.findById(session.getId()).orElseThrow().getOtpCode()).isNotNull();
    }

    private IdentityVerification pendingOtpSession() {
        IdentityVerification session = saveSession(vault, beneficiary, VerificationStatus.PENDING, 0);
        session.setOtpCode(otpHasher.hash(session.getId(), RIGHT_OTP));
        session.setOtpSentAt(LocalDateTime.now());
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        return verificationRepository.save(session);
    }

    private IdentityVerification saveSession(Vault v, User u, VerificationStatus status, int attempts) {
        return verificationRepository.save(IdentityVerification.builder().vault(v).beneficiary(u)
                .method(VerificationMethod.OTP).status(status).attemptCount(attempts).build());
    }

    private VerifyIdentityRequest request(String otp) {
        VerifyIdentityRequest r = new VerifyIdentityRequest();
        r.setVaultId(vault.getId());
        r.setVerificationMethod(VerificationMethod.OTP);
        r.setOtp(otp);
        return r;
    }

    private User newUser(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("Tester").passwordHash("x").status(UserStatus.ACTIVE).build());
    }
}
