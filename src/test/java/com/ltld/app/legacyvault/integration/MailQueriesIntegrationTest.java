package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.MailDataReader;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm các truy vấn mới của mail (N25, N6) trên MySQL thật. */
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
class MailQueriesIntegrationTest extends MySqlTestContainer {

    private static final String CHROME_WIN = "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0.1.2";
    private static final String CHROME_WIN_UPDATED = "Mozilla/5.0 (Windows NT 10.0) Chrome/131.5.0.0";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone) Safari/604.1";

    @MockitoBean private EmailSender emailSender;

    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private BeneficiaryClaimRepository claimRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private MailDataReader mailDataReader;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM beneficiary_claims");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM vaults");
        for (UUID id : users) {
            jdbcTemplate.update("DELETE FROM users WHERE BIN_TO_UUID(id) = ?", id.toString());
        }
        users.clear();
    }

    private User user(String prefix) {
        User u = userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com").fullName(prefix)
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        users.add(u.getId());
        return u;
    }

    private Vault vault(User owner, VaultStatus status, LocalDateTime deadline) {
        return vaultRepository.save(Vault.builder().owner(owner).name("V").status(status)
                .claimDeadlineAt(deadline).build());
    }

    private VaultBeneficiary beneficiary(Vault vault, User user, BeneficiaryInviteStatus status) {
        return vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(vault).user(user).email(user.getEmail())
                .fullName(user.getFullName()).status(status).build());
    }

    private void login(User user, String agent) {
        Instant now = Instant.now();
        refreshTokenRepository.saveAndFlush(RefreshToken.builder().user(user).tokenHash(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.DAYS)).userAgent(agent).build());
    }

    @Test
    void claimDeadlineQuery_onlyUnclaimedInWindowOfUnlockedVaults() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime inWindow = now.plusDays(6).plusHours(12);
        User owner = user("owner");
        Vault due = vault(owner, VaultStatus.UNLOCKED, inWindow);
        User waiting = user("waiting");
        User claimed = user("claimed");
        User declined = user("declined");
        VaultBeneficiary expected = beneficiary(due, waiting, BeneficiaryInviteStatus.ACCEPTED);
        beneficiary(due, claimed, BeneficiaryInviteStatus.ACCEPTED);
        beneficiary(due, declined, BeneficiaryInviteStatus.DECLINED);
        claimRepository.save(BeneficiaryClaim.builder().vault(due).beneficiary(claimed).status(ClaimStatus.CLAIMED)
                .claimDeadlineAt(inWindow).build());
        // két ngoài cửa sổ và két chưa mở khóa không được nhắc
        beneficiary(vault(owner, VaultStatus.UNLOCKED, now.plusDays(20)), waiting, BeneficiaryInviteStatus.ACCEPTED);
        beneficiary(vault(owner, VaultStatus.ACTIVE, inWindow), waiting, BeneficiaryInviteStatus.ACCEPTED);

        List<VaultBeneficiary> result =
                vaultBeneficiaryRepository.findUnclaimedWithDeadlineBetween(now.plusDays(6), now.plusDays(7));

        assertThat(result).extracting(VaultBeneficiary::getId).containsExactly(expected.getId());
    }

    @Test
    void newDevice_firstLoginAndKnownBrowserAreSilent_newBrowserIsReported() {
        User u = user("device");

        login(u, CHROME_WIN);
        assertThat(mailDataReader.newDeviceOf(u.getId())).isEmpty();

        login(u, CHROME_WIN_UPDATED);
        assertThat(mailDataReader.newDeviceOf(u.getId())).isEmpty();

        login(u, SAFARI_IPHONE);
        assertThat(mailDataReader.newDeviceOf(u.getId())).contains(SAFARI_IPHONE);
    }

}
