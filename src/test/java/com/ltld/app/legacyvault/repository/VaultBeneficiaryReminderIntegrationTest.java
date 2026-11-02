package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.integration.MySqlTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N2-exec: kiểm câu UPDATE markReminded trên MySQL thật (giới hạn 1 lần nhắc / 24h không cần khóa dòng).
 */
@SpringBootTest(properties = {
        "security.jwt.secret=integration-test-secret-key-at-least-32-bytes!!",
        "app.security.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
})
class VaultBeneficiaryReminderIntegrationTest extends MySqlTestContainer {

    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;

    private UUID ownerId;
    private UUID vaultId;
    private UUID beneficiaryId;

    @AfterEach
    void cleanUp() {
        if (beneficiaryId != null) vaultBeneficiaryRepository.deleteById(beneficiaryId);
        if (vaultId != null) vaultRepository.deleteById(vaultId);
        if (ownerId != null) userRepository.deleteById(ownerId);
    }

    @Test
    void markReminded_firstTimeThenWithin24hThenAfter24h() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(User.builder().email("owner-" + tag + "@example.com").fullName("Owner")
                .passwordHash("not-used").status(UserStatus.ACTIVE).build());
        ownerId = owner.getId();
        Vault vault = vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        vaultId = vault.getId();
        beneficiaryId = vaultBeneficiaryRepository.save(VaultBeneficiary.builder().vault(vault)
                .email("b-" + tag + "@example.com").fullName("B").build()).getId();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        // Lần đầu: chưa nhắc bao giờ -> ghi được
        assertThat(mark(now)).isEqualTo(1);
        // Ngay sau đó: chưa đủ 24h -> không ghi
        assertThat(mark(now.plusHours(23))).isZero();
        // Đủ 24h -> ghi được, và mốc mới là lần nhắc này
        assertThat(mark(now.plusHours(24))).isEqualTo(1);
        assertThat(vaultBeneficiaryRepository.findById(beneficiaryId).orElseThrow().getLastReminderAt())
                .isEqualTo(now.plusHours(24));
    }

    private int mark(LocalDateTime at) {
        return tx.execute(s -> vaultBeneficiaryRepository.markReminded(beneficiaryId, at, at.minusHours(24)));
    }
}
