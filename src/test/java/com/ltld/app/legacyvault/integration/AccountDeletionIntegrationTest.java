package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.accountdto.DeleteAccountRequest;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.accountservice.AccountDeletionService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Xoá tài khoản trên MySQL thật: điều kiện chặn (két, executor, người thụ hưởng) và ẩn danh hoá + thu hồi phiên + dừng DMS
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
class AccountDeletionIntegrationTest extends MySqlTestContainer {

    private static final String PASSWORD = "Secret-pass1";

    @MockitoBean private EmailSender emailSender;

    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID id : users) {
            String uid = id.toString();
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE BIN_TO_UUID(user_id) = ?", uid);
            jdbcTemplate.update("DELETE FROM executor_assignments WHERE BIN_TO_UUID(owner_id) = ? OR BIN_TO_UUID(executor_user_id) = ?", uid, uid);
            jdbcTemplate.update("DELETE b FROM vault_beneficiaries b JOIN vaults v ON v.id = b.vault_id WHERE BIN_TO_UUID(v.owner_id) = ?", uid);
            jdbcTemplate.update("DELETE FROM vaults WHERE BIN_TO_UUID(owner_id) = ?", uid);
            jdbcTemplate.update("DELETE FROM dms_configs WHERE BIN_TO_UUID(owner_id) = ?", uid);
            jdbcTemplate.update("DELETE FROM audit_logs WHERE BIN_TO_UUID(actor_id) = ?", uid);
        }
        for (UUID id : users) {
            jdbcTemplate.update("DELETE FROM users WHERE BIN_TO_UUID(id) = ?", id.toString());
        }
        users.clear();
    }

    private User user(String prefix) {
        User u = userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com").fullName("Nguyen Van A").phoneNumber("0901234567")
                .passwordHash(passwordEncoder.encode(PASSWORD)).status(UserStatus.ACTIVE).build());
        users.add(u.getId());
        return u;
    }

    private Vault vault(User owner) {
        return vaultRepository.save(Vault.builder().owner(owner).name("V").status(VaultStatus.ACTIVE).build());
    }

    private DeleteAccountRequest request(User u) {
        DeleteAccountRequest r = new DeleteAccountRequest();
        r.setPassword(PASSWORD);
        r.setConfirmEmail(u.getEmail());
        return r;
    }

    @Test
    void delete_anonymizesRevokesTokensAndSuspendsDms() {
        User u = user("leaver");
        String originalEmail = u.getEmail();
        dmsConfigRepository.save(DmsConfig.builder().owner(u).status(DmsStatus.ACTIVE).build());
        refreshTokenRepository.save(RefreshToken.builder().user(u).tokenHash("h-" + UUID.randomUUID())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());

        assertThat(accountDeletionService.check(u.getId()).isDeletable()).isTrue();
        accountDeletionService.delete(u.getId(), request(u));

        User after = userRepository.findById(u.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(after.getDeletedAt()).isNotNull();
        assertThat(after.getEmail()).isEqualTo("deleted-" + u.getId() + "@deleted.invalid");
        assertThat(after.getFullName()).isEqualTo("Deleted user");
        assertThat(after.getPhoneNumber()).isNull();
        assertThat(passwordEncoder.matches(PASSWORD, after.getPasswordHash())).isFalse();
        assertThat(userRepository.findByEmail(originalEmail)).isEmpty();   // email cũ đăng ký lại được
        assertThat(refreshTokenRepository.findActiveByUserId(u.getId(), Instant.now())).isEmpty();
        assertThat(dmsConfigRepository.findByOwnerId(u.getId()).orElseThrow().getStatus()).isEqualTo(DmsStatus.SUSPENDED);
    }

    @Test
    void check_listsEveryBlocker_andDeleteIsRefused() {
        User u = user("busy");
        vault(u);                                                            // còn két
        User other = user("owner");
        executorAssignmentRepository.save(ExecutorAssignment.builder().owner(other).executorUser(u)
                .executorType(ExecutorType.PRIMARY).status(ExecutorStatus.INACTIVE).build());   // còn là executor
        vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(vault(other)).user(u).email(u.getEmail())
                .status(BeneficiaryInviteStatus.ACCEPTED).build());          // còn là người thụ hưởng

        assertThat(accountDeletionService.check(u.getId()).getBlockers())
                .containsExactlyInAnyOrder("VAULTS_OWNED", "EXECUTOR", "BENEFICIARY");
        assertThatThrownBy(() -> accountDeletionService.delete(u.getId(), request(u)))
                .isInstanceOf(UserProfileException.class)
                .extracting(e -> ((UserProfileException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void declinedBeneficiaryAndRevokedExecutor_doNotBlock() {
        User u = user("past");
        User other = user("owner2");
        executorAssignmentRepository.save(ExecutorAssignment.builder().owner(other).executorUser(u)
                .executorType(ExecutorType.BACKUP).status(ExecutorStatus.REVOKED).build());
        vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(vault(other)).user(u).email(u.getEmail())
                .status(BeneficiaryInviteStatus.DECLINED).build());

        assertThat(accountDeletionService.check(u.getId()).getBlockers()).isEmpty();
    }

    @Test
    void beneficiaryOfFinishedVault_doesNotBlock() {
        User u = user("heir");
        User other = user("owner3");
        Vault claimed = vaultRepository.save(Vault.builder().owner(other).name("Done").status(VaultStatus.CLAIMED)
                .beneficiary(u).build());                                    // cả kiểu cũ (vaults.beneficiary_id)
        vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(claimed).user(u).email(u.getEmail())
                .status(BeneficiaryInviteStatus.ACCEPTED).build());          // lẫn kiểu N:M, két đã nhận xong

        assertThat(accountDeletionService.check(u.getId()).getBlockers()).isEmpty();
    }
}
