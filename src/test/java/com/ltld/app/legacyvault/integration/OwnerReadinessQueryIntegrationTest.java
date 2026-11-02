package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessItemResponse;
import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.readinessservice.OwnerReadinessService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

// F1: các truy vấn theo lô của điểm sẵn sàng chạy trên MySQL thật (mô hình N:M vault_beneficiaries / asset_allocations)
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
class OwnerReadinessQueryIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;            // không gửi mail thật

    @Autowired private OwnerReadinessService ownerReadinessService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private DigitalAssetRepository digitalAssetRepository;
    @Autowired private AssetAllocationRepository assetAllocationRepository;
    @Autowired private LegalDocumentRepository legalDocumentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private Vault twoBeneficiaryVault;     // 2 người thụ hưởng
    private Vault emptyVault;              // chưa có ai, chưa có tài sản
    private VaultBeneficiary ben1;
    private VaultBeneficiary ben2;
    private DigitalAsset activeAllocated;
    private DigitalAsset activeUnallocated;

    @BeforeEach
    void setUp() {
        owner = user("owner@example.com");
        twoBeneficiaryVault = vault(owner, "Two beneficiaries");
        emptyVault = vault(owner, "No beneficiary");
        ben1 = beneficiary(twoBeneficiaryVault, "a@example.com");
        ben2 = beneficiary(twoBeneficiaryVault, "b@example.com");

        activeAllocated = asset(twoBeneficiaryVault, AssetType.BANK_ACCOUNT, AssetStatus.ACTIVE);
        allocate(activeAllocated, ben1);
        allocate(activeAllocated, ben2);    // 2 dòng phân bổ cho cùng tài sản -> vẫn chỉ 1 id
        activeUnallocated = asset(twoBeneficiaryVault, AssetType.EMAIL, AssetStatus.ACTIVE);
        DigitalAsset revokedAllocated = asset(twoBeneficiaryVault, AssetType.EMAIL, AssetStatus.REVOKED);
        allocate(revokedAllocated, ben1);
        asset(twoBeneficiaryVault, AssetType.EMAIL, AssetStatus.REVOKED);   // REVOKED chưa phân bổ: bỏ qua

        document(twoBeneficiaryVault, DocumentType.WILL);
        document(twoBeneficiaryVault, DocumentType.WILL);                   // 2 bản WILL -> vẫn chỉ 1 id
        document(emptyVault, DocumentType.OWNERSHIP_PAPER);                 // không phải WILL

        // Dữ liệu của Owner khác không được lẫn vào kết quả
        User other = user("other@example.com");
        Vault otherVault = vault(other, "Other");
        VaultBeneficiary otherBen = beneficiary(otherVault, "c@example.com");
        allocate(asset(otherVault, AssetType.EMAIL, AssetStatus.ACTIVE), otherBen);
        document(otherVault, DocumentType.WILL);
    }

    @AfterEach
    void cleanUp() {
        // Con trước, cha sau (theo FK)
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM asset_allocations");
        jdbcTemplate.update("DELETE FROM digital_assets");
        jdbcTemplate.update("DELETE FROM legal_documents");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    // ---------- helpers ----------

    private User user(String email) {
        return userRepository.save(User.builder()
                .email(email).fullName("U").passwordHash("x").status(UserStatus.ACTIVE).build());
    }

    private Vault vault(User o, String name) {
        return vaultRepository.save(Vault.builder().owner(o).name(name).status(VaultStatus.ACTIVE).build());
    }

    private VaultBeneficiary beneficiary(Vault v, String email) {
        return vaultBeneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(v).email(email).status(BeneficiaryInviteStatus.PENDING_REGISTRATION).build());
    }

    private DigitalAsset asset(Vault v, AssetType type, AssetStatus status) {
        return digitalAssetRepository.save(DigitalAsset.builder()
                .vault(v).assetType(type).assetName(type.name())
                .encryptedSecret("ENC:abc").encryptionKeyRef("v1").status(status).build());
    }

    private void allocate(DigitalAsset a, VaultBeneficiary b) {
        assetAllocationRepository.save(AssetAllocation.builder()
                .asset(a).beneficiary(b).mode(AllocationMode.EXCLUSIVE).build());
    }

    private void document(Vault v, DocumentType type) {
        legalDocumentRepository.save(LegalDocument.builder()
                .vault(v).documentType(type).fileName("doc.pdf").fileUrlEncrypted("ENC:url").build());
    }

    private static ReadinessItemResponse item(ReadinessResponse res, String key) {
        return res.getItems().stream().filter(i -> i.getKey().equals(key)).findFirst().orElseThrow();
    }

    // ---------- truy vấn ----------

    @Test
    @DisplayName("F1: vault có 2 người thụ hưởng trả 1 id; vault chưa có ai và vault của Owner khác không có mặt")
    void findVaultIdsWithBeneficiaryByOwnerId_distinctAndOwnerScoped() {
        assertThat(vaultBeneficiaryRepository.findVaultIdsWithBeneficiaryByOwnerId(owner.getId()))
                .containsExactly(twoBeneficiaryVault.getId());
    }

    @Test
    @DisplayName("F1: chỉ trả tài sản ACTIVE đã có dòng phân bổ, không trùng, không lẫn Owner khác")
    void findAllocatedActiveAssetIdsByOwnerId_onlyActiveAllocated() {
        assertThat(assetAllocationRepository.findAllocatedActiveAssetIdsByOwnerId(owner.getId()))
                .containsExactly(activeAllocated.getId());
    }

    @Test
    @DisplayName("F1: chỉ trả vault có WILL của Owner, không trùng")
    void findVaultIdsWithDocumentTypeByOwnerId_onlyWill() {
        assertThat(legalDocumentRepository.findVaultIdsWithDocumentTypeByOwnerId(owner.getId(), DocumentType.WILL))
                .containsExactly(twoBeneficiaryVault.getId());
        assertThat(legalDocumentRepository.findVaultIdsWithDocumentTypeByOwnerId(owner.getId(),
                DocumentType.OWNERSHIP_PAPER)).containsExactly(emptyVault.getId());
    }

    // ---------- service trên dữ liệu thật ----------

    @Test
    @DisplayName("F1: còn tài sản ACTIVE chưa phân bổ -> chưa đạt; phân bổ nốt thì đạt (REVOKED chưa phân bổ không chặn)")
    void evaluateReadiness_allocationCriterion_onRealDatabase() {
        ReadinessResponse before = ownerReadinessService.evaluateReadiness(owner.getId());
        ReadinessItemResponse allocBefore = item(before, "ASSET_BENEFICIARY_ALLOCATED");
        assertThat(allocBefore.isDone()).isFalse();
        assertThat(allocBefore.getFixUrl()).isEqualTo("/owner/vaults/" + twoBeneficiaryVault.getId());
        assertThat(item(before, "MASTER_VAULT_AND_ASSET").isDone()).isTrue();
        assertThat(item(before, "LEGAL_WILL_DOCUMENT").isDone()).isTrue();

        allocate(activeUnallocated, ben2);

        ReadinessResponse after = ownerReadinessService.evaluateReadiness(owner.getId());
        assertThat(item(after, "ASSET_BENEFICIARY_ALLOCATED").isDone()).isTrue();
        assertThat(after.getScore()).isEqualTo(before.getScore() + 15);
    }
}
