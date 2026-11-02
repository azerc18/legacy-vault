package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.beneficiarydto.InheritanceDossierData;
import com.ltld.app.legacyvault.dto.beneficiarydto.LegalDocumentSummaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.IdentityVerification;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class DocumentVersioningIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private LegalDocumentRepository legalDocumentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Autowired private BeneficiaryClaimRepository claimRepository;
    @Autowired private IdentityVerificationRepository verificationRepository;
    @Autowired private BeneficiaryService beneficiaryService;
    @Autowired private VaultService vaultService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User owner;
    private User beneficiary;
    private Vault unlockedVault;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email("owner-x5@example.com")
                .fullName("Owner X5")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build());

        beneficiary = userRepository.save(User.builder()
                .email("ben-x5@example.com")
                .fullName("Beneficiary X5")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build());

        unlockedVault = vaultRepository.save(Vault.builder()
                .owner(owner)
                .beneficiary(beneficiary)
                .beneficiaryEmail(beneficiary.getEmail())
                .name("Vault X5")
                .status(VaultStatus.UNLOCKED)
                .unlockedAt(LocalDateTime.now().minusDays(1))
                .claimDeadlineAt(LocalDateTime.now().plusDays(30))
                .build());

        vaultBeneficiaryRepository.save(VaultBeneficiary.builder()
                .vault(unlockedVault)
                .user(beneficiary)
                .email(beneficiary.getEmail())
                .fullName(beneficiary.getFullName())
                .status(BeneficiaryInviteStatus.ACCEPTED)
                .build());

        claimRepository.save(BeneficiaryClaim.builder()
                .vault(unlockedVault)
                .beneficiary(beneficiary)
                .status(ClaimStatus.PENDING)
                .claimDeadlineAt(LocalDateTime.now().plusDays(30))
                .build());

        verificationRepository.save(IdentityVerification.builder()
                .vault(unlockedVault)
                .beneficiary(beneficiary)
                .method(VerificationMethod.OTP)
                .status(VerificationStatus.SUCCESS)
                .verifiedAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM identity_verifications");
        jdbcTemplate.update("DELETE FROM beneficiary_claims");
        jdbcTemplate.update("DELETE FROM legal_documents");
        jdbcTemplate.update("DELETE FROM vault_beneficiaries");
        jdbcTemplate.update("DELETE FROM vaults");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("M1: Lưu LegalDocument không đặt documentGroupId thì @PrePersist tự sinh giá trị mặc định")
    void saveWithoutDocumentGroupId_AssignsDefaultUuid() {
        LegalDocument saved = legalDocumentRepository.save(LegalDocument.builder()
                .vault(unlockedVault)
                .documentType(DocumentType.WILL)
                .fileName("dichuc.pdf")
                .fileUrlEncrypted("uploads/dichuc.bin")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDocumentGroupId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("M2: Vault có v1 (cũ) và v2 (hiện hành) -> người thụ hưởng, hồ sơ PDF và totalDocuments chỉ thấy v2")
    void vaultWithV1AndV2_BeneficiaryAndCountOnlySeeCurrentVersion() {
        UUID groupId = UUID.randomUUID();
        LegalDocument v1 = legalDocumentRepository.save(LegalDocument.builder()
                .vault(unlockedVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v1_cu.pdf")
                .fileUrlEncrypted("uploads/v1.bin")
                .version(1)
                .isCurrent(false)
                .build());

        LegalDocument v2 = legalDocumentRepository.save(LegalDocument.builder()
                .vault(unlockedVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v2_moi.pdf")
                .fileUrlEncrypted("uploads/v2.bin")
                .version(2)
                .isCurrent(true)
                .build());

        // 1. Danh sách tài liệu cho người thụ hưởng chỉ có v2
        List<LegalDocumentSummaryResponse> docs = beneficiaryService.getVaultDocuments(unlockedVault.getId(), beneficiary.getId());
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getId()).isEqualTo(v2.getId());
        assertThat(docs.get(0).getFileName()).isEqualTo("dichuc_v2_moi.pdf");

        // 2. Tải bản cũ v1 bị chặn 404 Not Found
        assertThatThrownBy(() -> beneficiaryService.downloadVaultDocument(unlockedVault.getId(), v1.getId(), beneficiary.getId()))
                .isInstanceOf(BeneficiaryException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);

        // 3. Hồ sơ PDF (X4) chỉ liệt kê bản hiện hành v2
        InheritanceDossierData dossier = beneficiaryService.getDossierData(unlockedVault.getId(), beneficiary.getId());
        assertThat(dossier.documents()).hasSize(1);
        assertThat(dossier.documents().get(0).fileName()).isEqualTo("dichuc_v2_moi.pdf");

        // 4. VaultDetail.totalDocuments chỉ đếm bản hiện hành (1 bản)
        VaultDetailResponse detail = vaultService.getVaultDetail(unlockedVault.getId(), owner.getId());
        assertThat(detail.getTotalDocuments()).isEqualTo(1L);
    }
}

