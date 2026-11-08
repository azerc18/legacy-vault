package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryLockResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryLockAdminService;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// #10: người thụ hưởng sai 5 lần bị khóa 24h; Admin mở khóa thì thử lại được ngay, có audit log
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
class AdminBeneficiaryUnlockIntegrationTest extends MySqlTestContainer {

    private static final String RIGHT_OTP = "111111";
    private static final String WRONG_OTP = "222222";

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private BeneficiaryService beneficiaryService;
    @Autowired private BeneficiaryLockAdminService lockAdminService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private BeneficiaryClaimRepository claimRepository;
    @Autowired private IdentityVerificationRepository verificationRepository;
    @Autowired private OtpHasher otpHasher;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Vault vault;
    private User beneficiary;
    private User admin;

    @BeforeEach
    void setUp() {
        User owner = newUser("owner@example.com");
        admin = newUser("admin@example.com");
        beneficiary = newUser("ben@example.com");
        vault = vaultRepository.save(Vault.builder().owner(owner).name("Két gia đình").status(VaultStatus.UNLOCKED)
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
    void lockedBeneficiary_adminUnlocks_canVerifyAgain() {
        // Một phiên PENDING khác trong 24h đã có 2 lượt sai: sau khi mở khóa cũng phải được xóa
        IdentityVerification earlier = pendingOtpSession();
        jdbcTemplate.update("UPDATE identity_verifications SET attempt_count = 2 WHERE id = UUID_TO_BIN(?)",
                earlier.getId().toString());
        jdbcTemplate.update("UPDATE identity_verifications SET created_at = ? WHERE id = UUID_TO_BIN(?)",
                LocalDateTime.now().minusMinutes(30), earlier.getId().toString());
        lockOut();

        List<BeneficiaryLockResponse> locks = lockAdminService.listActiveLocks();
        assertThat(locks).singleElement().satisfies(l -> {
            assertThat(l.getVaultId()).isEqualTo(vault.getId());
            assertThat(l.getBeneficiaryEmail()).isEqualTo("ben@example.com");
            assertThat(l.getLockedUntil()).isAfter(LocalDateTime.now().plusHours(23));
        });

        lockAdminService.unlock(admin.getId(), vault.getId(), beneficiary.getId(), "called support");

        assertThat(lockAdminService.listActiveLocks()).isEmpty();
        assertThat(verificationRepository.findById(earlier.getId()).orElseThrow().getAttemptCount()).isZero();
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'IDENTITY_UNLOCKED_BY_ADMIN'", Integer.class);
        assertThat(audits).isEqualTo(1);

        // Phiên mới: OTP đúng thì xác minh thành công, không còn 423
        pendingOtpSession();
        assertThat(beneficiaryService.verifyIdentity(request(RIGHT_OTP), beneficiary.getId()).getStatus())
                .isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void unlock_notLocked_throws404() {
        assertThatThrownBy(() -> lockAdminService.unlock(admin.getId(), vault.getId(), beneficiary.getId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void lockOut() {
        pendingOtpSession();
        HttpStatus last = null;
        for (int i = 0; i < 5 && last != HttpStatus.LOCKED; i++) {
            last = catchThrowableOfType(BeneficiaryException.class,
                    () -> beneficiaryService.verifyIdentity(request(WRONG_OTP), beneficiary.getId())).getStatus();
        }
        assertThat(last).isEqualTo(HttpStatus.LOCKED);
    }

    private IdentityVerification pendingOtpSession() {
        IdentityVerification session = verificationRepository.save(IdentityVerification.builder()
                .vault(vault).beneficiary(beneficiary).method(VerificationMethod.OTP)
                .status(VerificationStatus.PENDING).attemptCount(0).build());
        session.setOtpCode(otpHasher.hash(session.getId(), RIGHT_OTP));
        session.setOtpSentAt(LocalDateTime.now());
        session.setOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        return verificationRepository.save(session);
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
