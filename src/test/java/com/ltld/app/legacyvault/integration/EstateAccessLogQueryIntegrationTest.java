package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.auditservice.OwnerActivityLogService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F6 (m3): query "Ai đã xem gì" trên MySQL thật. Log ghi qua AuditLogService với đúng tham số như nơi ghi thật
 * (AssetServiceImpl, BeneficiaryServiceImpl, LegalVerificationServiceImpl).
 */
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
class EstateAccessLogQueryIntegrationTest extends MySqlTestContainer {

    @MockitoBean
    private EmailSender emailSender;                                     // không gửi mail thật
    @MockitoBean
    private ExecutorNotificationService executorNotificationService;   // không gửi mail thật

    @Autowired private OwnerActivityLogService ownerActivityLogService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Dữ liệu test tạo ra, để @AfterEach chỉ xóa đúng phần của mình.
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> vaultIds = new ArrayList<>();
    private final List<UUID> requestIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID vaultId : vaultIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE BIN_TO_UUID(vault_id) = ?", vaultId.toString());
        }
        for (UUID requestId : requestIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE BIN_TO_UUID(entity_id) = ?", requestId.toString());
        }
        for (UUID userId : userIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE BIN_TO_UUID(actor_id) = ?", userId.toString());
        }
        legalVerificationRequestRepository.deleteAllById(requestIds);
        vaultRepository.deleteAllById(vaultIds);
        userRepository.deleteAllById(userIds);
    }

    @Test
    void showsOthersAccessOnOwnEstate_hidesOwnerSelfViewAndOtherOwners() {
        User owner = user("owner");
        User beneficiary = user("beneficiary");
        User verifier = user("verifier");
        User executor = user("executor");
        Vault vault = vault(owner);

        User otherOwner = user("other-owner");
        Vault otherVault = vault(otherOwner);
        LegalVerificationRequest request = request(owner, executor);
        LegalVerificationRequest otherRequest = request(otherOwner, executor);

        // Hiện: người khác xem / tải dữ liệu di sản của Owner
        UUID assetViewed = log(AuditAction.ASSET_VIEWED, beneficiary, vault.getId(), "DigitalAsset", UUID.randomUUID());
        UUID documentDownloaded = log(AuditAction.DOCUMENT_DOWNLOADED, beneficiary, vault.getId(), "LegalDocument", UUID.randomUUID());
        UUID executorViewed = log(AuditAction.EXECUTOR_VAULT_VIEWED, executor, vault.getId(), "Vault", vault.getId());
        // M3: Verifier xem giấy chứng tử, vaultId null, khớp theo hồ sơ của Owner
        UUID legalViewed = log(AuditAction.LEGAL_DOCUMENT_VIEWED, verifier, null, "LegalVerificationRequest", request.getId());

        // Ẩn: Owner tự xem (M1), log của Owner khác, action không thuộc danh sách
        UUID ownerSelfView = log(AuditAction.ASSET_VIEWED, owner, vault.getId(), "DigitalAsset", UUID.randomUUID());
        UUID ownerSelfDownload = log(AuditAction.DOCUMENT_DOWNLOADED, owner, vault.getId(), "LegalDocument", UUID.randomUUID());
        UUID otherOwnerVault = log(AuditAction.ASSET_VIEWED, beneficiary, otherVault.getId(), "DigitalAsset", UUID.randomUUID());
        UUID otherOwnerRequest = log(AuditAction.LEGAL_DOCUMENT_VIEWED, verifier, null, "LegalVerificationRequest", otherRequest.getId());
        UUID notAccessAction = log(AuditAction.VAULT_UPDATED, beneficiary, vault.getId(), "Vault", vault.getId());

        Page<VaultActivityLogResponse> logs = ownerActivityLogService.getEstateAccessLogs(
                owner.getId(), null, null, PageRequest.of(0, 50));

        assertThat(logs.getContent()).extracting(VaultActivityLogResponse::getId)
                .containsExactlyInAnyOrder(assetViewed, documentDownloaded, executorViewed, legalViewed)
                .doesNotContain(ownerSelfView, ownerSelfDownload, otherOwnerVault, otherOwnerRequest, notAccessAction);
        assertThat(logs.getContent()).extracting(VaultActivityLogResponse::getActorName).doesNotContain("You");
        assertThat(logs.getContent())
                .filteredOn(r -> r.getId().equals(executorViewed))
                .singleElement()
                .satisfies(r -> assertThat(r.getDescription()).isEqualTo("Your executor viewed the vault's asset list."));

        // Trang nhỏ hơn tổng số dòng -> Spring Data phải chạy countQuery; phải lọc giống query chính
        Page<VaultActivityLogResponse> firstOfOne = ownerActivityLogService.getEstateAccessLogs(
                owner.getId(), null, null, PageRequest.of(0, 1));
        assertThat(firstOfOne.getContent()).hasSize(1);
        assertThat(firstOfOne.getTotalElements()).isEqualTo(4);

        // Owner khác chỉ thấy phần của mình
        Page<VaultActivityLogResponse> otherLogs = ownerActivityLogService.getEstateAccessLogs(
                otherOwner.getId(), null, null, PageRequest.of(0, 50));
        assertThat(otherLogs.getContent()).extracting(VaultActivityLogResponse::getId)
                .containsExactlyInAnyOrder(otherOwnerVault, otherOwnerRequest);
    }

    @Test
    void dateRange_excludesLogsOutsideRange_inContentAndCount() {
        User owner = user("owner-range");
        User beneficiary = user("beneficiary-range");
        Vault vault = vault(owner);

        UUID recent = log(AuditAction.ASSET_VIEWED, beneficiary, vault.getId(), "DigitalAsset", UUID.randomUUID());
        UUID old = log(AuditAction.ASSET_DOWNLOADED, beneficiary, vault.getId(), "DigitalAsset", UUID.randomUUID());
        jdbcTemplate.update("UPDATE audit_logs SET created_at = ? WHERE BIN_TO_UUID(id) = ?",
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)), old.toString());

        Instant from = Instant.now().minus(5, ChronoUnit.DAYS);
        Page<VaultActivityLogResponse> lastFiveDays = ownerActivityLogService.getEstateAccessLogs(
                owner.getId(), from, Instant.now(), PageRequest.of(0, 1));
        Page<VaultActivityLogResponse> all = ownerActivityLogService.getEstateAccessLogs(
                owner.getId(), null, null, PageRequest.of(0, 1));

        assertThat(lastFiveDays.getContent()).extracting(VaultActivityLogResponse::getId).containsExactly(recent);
        assertThat(lastFiveDays.getTotalElements()).isEqualTo(1);
        // Mới nhất trước, và không truyền from thì log 10 ngày trước vẫn được đếm
        assertThat(all.getContent()).extracting(VaultActivityLogResponse::getId).containsExactly(recent);
        assertThat(all.getTotalElements()).isEqualTo(2);
    }

    // ---------- dữ liệu ----------

    // Ghi như nơi ghi thật, rồi trả về id log vừa ghi (mỗi actor + action + entity trong test là duy nhất)
    private UUID log(AuditAction action, User actor, UUID vaultId, String entityType, UUID entityId) {
        auditLogService.log(action, AuditResult.SUCCESS, actor.getId(), actor.getEmail(), vaultId,
                entityType, entityId.toString(), null);
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM audit_logs WHERE action = ? AND BIN_TO_UUID(actor_id) = ?"
                        + " AND BIN_TO_UUID(entity_id) = ?",
                String.class, action.name(), actor.getId().toString(), entityId.toString()));
    }

    private Vault vault(User owner) {
        Vault vault = vaultRepository.save(Vault.builder().owner(owner).name("Vault " + owner.getFullName()).build());
        vaultIds.add(vault.getId());
        return vault;
    }

    private LegalVerificationRequest request(User owner, User executor) {
        LegalVerificationRequest request = legalVerificationRequestRepository.save(LegalVerificationRequest.builder()
                .owner(owner)
                .executor(executor)
                .deathCertificateFileUrlEncrypted("not-used")
                .build());
        requestIds.add(request.getId());
        return request;
    }

    private User user(String name) {
        String tag = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .email(tag + "@example.com")
                .fullName(tag)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(user.getId());
        return user;
    }
}
