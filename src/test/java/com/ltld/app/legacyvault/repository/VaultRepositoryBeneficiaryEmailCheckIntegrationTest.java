package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.integration.MySqlTestContainer;
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

/**
 * FR-06 (Q26): kiểm câu JPQL existsBeneficiaryWithEmail trên MySQL thật
 * (left join sang users để bắt cả Vault chưa liên kết beneficiary, lower(email) ở cả hai cột).
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
class VaultRepositoryBeneficiaryEmailCheckIntegrationTest extends MySqlTestContainer {

    @Autowired
    private VaultRepository vaultRepository;
    @Autowired
    private UserRepository userRepository;

    // Dữ liệu test tạo ra, để @AfterEach chỉ xóa đúng phần của mình (vault trước, user sau vì FK).
    private final List<UUID> vaultIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        vaultRepository.deleteAllById(vaultIds);
        userRepository.deleteAllById(userIds);
        vaultIds.clear();
        userIds.clear();
    }

    @Test
    void linkedBeneficiaryOfOwner_matches() {
        User owner = user("owner");
        User beneficiary = user("beneficiary");
        vault(owner, beneficiary, null);

        assertThat(vaultRepository.existsBeneficiaryWithEmail(owner.getId(), beneficiary.getEmail())).isTrue();
    }

    @Test
    void pendingBeneficiaryEmailOfOwner_matches() {
        User owner = user("owner");
        String pendingEmail = email("pending");
        vault(owner, null, pendingEmail);

        assertThat(vaultRepository.existsBeneficiaryWithEmail(owner.getId(), pendingEmail)).isTrue();
    }

    @Test
    void beneficiaryOfAnotherOwner_doesNotMatch() {
        User owner = user("owner");
        User otherOwner = user("other-owner");
        User beneficiary = user("beneficiary");
        String pendingEmail = email("pending");
        vault(otherOwner, beneficiary, null);
        vault(otherOwner, null, pendingEmail);

        assertThat(vaultRepository.existsBeneficiaryWithEmail(owner.getId(), beneficiary.getEmail())).isFalse();
        assertThat(vaultRepository.existsBeneficiaryWithEmail(owner.getId(), pendingEmail)).isFalse();
    }

    @Test
    void mixedCaseStoredEmail_matchesLowercaseParameter() {
        User owner = user("owner");
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User linked = save("Mixed.Case-" + tag + "@Example.COM");
        vault(owner, linked, null);
        vault(owner, null, "Pending.Case-" + tag + "@Example.COM");

        assertThat(vaultRepository.existsBeneficiaryWithEmail(
                owner.getId(), "mixed.case-" + tag + "@example.com")).isTrue();
        assertThat(vaultRepository.existsBeneficiaryWithEmail(
                owner.getId(), "pending.case-" + tag + "@example.com")).isTrue();
    }

    // FR-07/FR-14: dashboard Executor chỉ đếm Vault còn hiệu lực, bỏ Vault ARCHIVED_LOCKED
    @Test
    void countByOwnerIds_skipsArchivedLockedVaults() {
        User owner = user("owner");
        vault(owner, null, email("a"));
        Vault archived = vault(owner, null, email("b"));
        archived.setStatus(VaultStatus.ARCHIVED_LOCKED);
        vaultRepository.save(archived);

        List<Object[]> rows = vaultRepository.countByOwnerIds(List.of(owner.getId()));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row[0]).isEqualTo(owner.getId());
            assertThat(row[1]).isEqualTo(1L);
        });
    }

    // ---------- helpers ----------

    private static String email(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private User user(String name) {
        return save(email(name));
    }

    private User save(String email) {
        User u = userRepository.save(User.builder()
                .email(email)
                .fullName(email)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(u.getId());
        return u;
    }

    private Vault vault(User owner, User beneficiary, String beneficiaryEmail) {
        Vault v = vaultRepository.save(Vault.builder()
                .owner(owner)
                .beneficiary(beneficiary)
                .beneficiaryEmail(beneficiaryEmail)
                .name("Vault " + UUID.randomUUID().toString().substring(0, 8))
                .build());
        vaultIds.add(v.getId());
        return v;
    }
}
