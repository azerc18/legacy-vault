package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// N2-access: chạy thật query findAssetIdsNotHeldBy trên MySQL (unit test chỉ mock repository)
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
class AssetAllocationAccessIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;            // không gửi mail thật

    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private DigitalAssetRepository digitalAssetRepository;
    @Autowired private AssetAllocationRepository assetAllocationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Vault vault;
    private VaultBeneficiary a;   // người đang được kiểm tra ("mình")
    private VaultBeneficiary b;   // người khác

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder()
                .email("owner@example.com").fullName("Owner").passwordHash("x").status(UserStatus.ACTIVE).build());
        vault = vaultRepository.save(Vault.builder().owner(owner).name("V").status(VaultStatus.ACTIVE).build());
        a = beneficiary("a@example.com");
        b = beneficiary("b@example.com");
    }

    @AfterEach
    void cleanUp() {
        // Con trước, cha sau (theo FK)
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM asset_allocations");
        jdbcTemplate.update("DELETE FROM digital_assets");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private VaultBeneficiary beneficiary(String email) {
        return vaultBeneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(vault).email(email).status(BeneficiaryInviteStatus.PENDING_REGISTRATION).build());
    }

    private DigitalAsset asset(String name, AssetStatus status) {
        return digitalAssetRepository.save(DigitalAsset.builder()
                .vault(vault).assetType(AssetType.BANK_ACCOUNT).assetName(name)
                .encryptedSecret("ENC:abc").encryptionKeyRef("v1").status(status).build());
    }

    private AssetAllocation exclusive(DigitalAsset asset, VaultBeneficiary primary) {
        return assetAllocationRepository.save(AssetAllocation.builder()
                .asset(asset).beneficiary(primary).mode(AllocationMode.EXCLUSIVE).build());
    }

    private List<UUID> hiddenFor(VaultBeneficiary who) {
        return assetAllocationRepository.findAssetIdsNotHeldBy(vault.getId(), who.getId());
    }

    @Test
    void assetAllocatedToMe_isNotHiddenFromMe_butHiddenFromOthers() {
        DigitalAsset x = asset("X", AssetStatus.ACTIVE);
        exclusive(x, a);

        assertThat(hiddenFor(a)).doesNotContain(x.getId());
        assertThat(hiddenFor(b)).contains(x.getId());
    }

    @Test
    void assetAllocatedToOther_isHiddenFromMe() {
        DigitalAsset y = asset("Y", AssetStatus.ACTIVE);
        exclusive(y, b);

        assertThat(hiddenFor(a)).containsExactly(y.getId());
    }

    @Test
    void assetWithoutAnyAllocation_isHiddenFromNobody() {   // Q47 phương án b
        DigitalAsset z = asset("Z", AssetStatus.ACTIVE);

        assertThat(hiddenFor(a)).doesNotContain(z.getId());
        assertThat(hiddenFor(b)).doesNotContain(z.getId());
    }

    @Test
    void afterFallback_effectiveHolderSeesIt_andOriginalPrimaryDoesNot() {
        DigitalAsset w = asset("W", AssetStatus.ACTIVE);
        // Người chính b đã từ chối, phần được chuyển sang người dự phòng a
        assetAllocationRepository.save(AssetAllocation.builder()
                .asset(w).beneficiary(b).contingentBeneficiary(a).effectiveBeneficiary(a)
                .mode(AllocationMode.EXCLUSIVE).build());

        assertThat(hiddenFor(a)).doesNotContain(w.getId());
        assertThat(hiddenFor(b)).contains(w.getId());
    }

    @Test
    void contingentBeforeFallback_isHidden() {
        DigitalAsset v = asset("V", AssetStatus.ACTIVE);
        assetAllocationRepository.save(AssetAllocation.builder()
                .asset(v).beneficiary(b).contingentBeneficiary(a).mode(AllocationMode.EXCLUSIVE).build());

        assertThat(hiddenFor(a)).contains(v.getId());   // chưa fallback thì người dự phòng chưa có quyền
    }

    @Test
    void proportionalAsset_isNotHiddenFromAnyHolder_andNotDuplicated() {
        DigitalAsset p = asset("P", AssetStatus.ACTIVE);
        assetAllocationRepository.save(AssetAllocation.builder().asset(p).beneficiary(a)
                .mode(AllocationMode.PROPORTIONAL).sharePercent(new BigDecimal("60.00")).build());
        assetAllocationRepository.save(AssetAllocation.builder().asset(p).beneficiary(b)
                .mode(AllocationMode.PROPORTIONAL).sharePercent(new BigDecimal("40.00")).build());
        DigitalAsset other = asset("Other", AssetStatus.ACTIVE);
        VaultBeneficiary c = beneficiary("c@example.com");
        assetAllocationRepository.save(AssetAllocation.builder().asset(other).beneficiary(c)
                .mode(AllocationMode.EXCLUSIVE).build());

        assertThat(hiddenFor(a)).doesNotContain(p.getId());
        assertThat(hiddenFor(b)).doesNotContain(p.getId());
        assertThat(hiddenFor(a)).containsExactly(other.getId());   // DISTINCT: không trùng id
    }

    @Test
    void revokedAsset_neverAppearsInHiddenList() {
        DigitalAsset r = asset("R", AssetStatus.REVOKED);
        exclusive(r, b);

        assertThat(hiddenFor(a)).doesNotContain(r.getId());
    }
}