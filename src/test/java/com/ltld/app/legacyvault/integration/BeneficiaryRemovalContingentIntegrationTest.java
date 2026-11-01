package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// N2-owner (m6c): xóa người đang là người DỰ PHÒNG của một tài sản → không lỗi FK,
// dòng phân bổ còn nguyên và contingent_beneficiary = NULL
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
class BeneficiaryRemovalContingentIntegrationTest extends MySqlTestContainer{

    @MockitoBean
    private EmailSender emailSender;            // không gửi mail thật

    @Autowired private VaultService vaultService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private DigitalAssetRepository digitalAssetRepository;
    @Autowired private AssetAllocationRepository assetAllocationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private Vault vault;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("owner@example.com").fullName("Owner").passwordHash("x").status(UserStatus.ACTIVE).build());
        vault = vaultRepository.save(Vault.builder().owner(owner).name("V").status(VaultStatus.ACTIVE).build());
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

    @Test
    void removeBeneficiary_whoIsOnlyContingent_clearsContingentAndKeepsAllocation() {
        VaultBeneficiary a = beneficiary("a@example.com");
        VaultBeneficiary b = beneficiary("b@example.com");   // chỉ là người dự phòng
        DigitalAsset asset = digitalAssetRepository.save(DigitalAsset.builder()
                .vault(vault).assetType(AssetType.BANK_ACCOUNT).assetName("MB Bank")
                .encryptedSecret("ENC:abc").encryptionKeyRef("v1").status(AssetStatus.ACTIVE).build());
        assetAllocationRepository.save(AssetAllocation.builder()
                .asset(asset).beneficiary(a).contingentBeneficiary(b).mode(AllocationMode.EXCLUSIVE).build());

        vaultService.removeBeneficiary(owner.getId(), vault.getId(), b.getId());   // không được ném lỗi FK

        List<AssetAllocation> rows = assetAllocationRepository.findByAssetId(asset.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getContingentBeneficiary()).isNull();
        assertThat(vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vault.getId()))
                .extracting(VaultBeneficiary::getId).containsExactly(a.getId());
    }
}