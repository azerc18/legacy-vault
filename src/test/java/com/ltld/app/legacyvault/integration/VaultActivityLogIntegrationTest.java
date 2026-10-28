package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.auditservice.VaultActivityLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-05 (m2): nhật ký Vault trên MySQL thật.
 * Kiểm các hành động nội bộ của Admin (OWNER_HIDDEN_ACTIONS) bị ẩn khỏi nhật ký Vault:
 * - không xuất hiện trong content
 * - totalElements không đếm những dòng đó (countQuery cũng lọc)
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
@Testcontainers(disabledWithoutDocker = true)
class VaultActivityLogIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;

    @Autowired private VaultActivityLogService vaultActivityLogService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> vaultIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        vaultIds.forEach(id -> vaultRepository.findById(id).ifPresent(vaultRepository::delete));
        userRepository.deleteAllById(userIds);
    }

    /**
     * FR-05 (Q42, m2): Admin gọi getAdminVaultDetail → ghi ADMIN_VAULT_VIEWED.
     * Owner gọi getVaultActivityLogs → ADMIN_VAULT_VIEWED không xuất hiện,
     * và totalElements chỉ đếm các log không bị ẩn.
     */
    @Test
    void adminVaultViewed_isHiddenFromVaultActivityLog() {
        User owner = saveUser("vault-log-owner");
        User admin = saveUser("vault-log-admin");
        Vault vault = vaultRepository.save(
                Vault.builder().owner(owner).name("Test Vault").build());
        vaultIds.add(vault.getId());

        // Ghi 2 log của Owner (sẽ hiện trong nhật ký)
        auditLogService.log(AuditAction.VAULT_CREATED, AuditResult.SUCCESS,
                owner.getId(), owner.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), null);
        auditLogService.log(AuditAction.BENEFICIARY_ASSIGNED, AuditResult.SUCCESS,
                owner.getId(), owner.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "beneficiaryUserId=" + UUID.randomUUID());

        // Ghi log ẩn: Admin xem Vault (ADMIN_VAULT_VIEWED)
        auditLogService.log(AuditAction.ADMIN_VAULT_VIEWED, AuditResult.SUCCESS,
                admin.getId(), admin.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "Admin viewed vault detail");

        // Ghi log ẩn: DMS_SUSPENDED (Admin tạm dừng DMS của Owner)
        auditLogService.log(AuditAction.DMS_SUSPENDED, AuditResult.SUCCESS,
                admin.getId(), admin.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "ownerId=" + owner.getId());

        Page<VaultActivityLogResponse> result = vaultActivityLogService.getVaultActivityLogs(
                vault.getId(), owner.getId(), null, null, PageRequest.of(0, 20));

        // 1. Chỉ 2 log của Owner hiện ra — ADMIN_VAULT_VIEWED và DMS_SUSPENDED bị ẩn
        assertThat(result.getContent())
                .extracting(VaultActivityLogResponse::getAction)
                .containsExactlyInAnyOrder(AuditAction.VAULT_CREATED, AuditAction.BENEFICIARY_ASSIGNED)
                .doesNotContain(AuditAction.ADMIN_VAULT_VIEWED, AuditAction.DMS_SUSPENDED);

        // 2. totalElements không đếm log bị ẩn (countQuery cũng lọc — FR-05 A)
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    /**
     * FR-05 (m2): DMS_RESUMED cũng thuộc OWNER_HIDDEN_ACTIONS — kiểm cả 3 action ẩn trong một vault.
     */
    @Test
    void allHiddenActions_areExcludedFromVaultLog_andCountIsCorrect() {
        User owner = saveUser("vault-log-owner2");
        User admin = saveUser("vault-log-admin2");
        Vault vault = vaultRepository.save(
                Vault.builder().owner(owner).name("Hidden Actions Vault").build());
        vaultIds.add(vault.getId());

        // 1 log hiển thị
        auditLogService.log(AuditAction.VAULT_UPDATED, AuditResult.SUCCESS,
                owner.getId(), owner.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), null);

        // Tất cả 3 action thuộc OWNER_HIDDEN_ACTIONS
        auditLogService.log(AuditAction.ADMIN_VAULT_VIEWED, AuditResult.SUCCESS,
                admin.getId(), admin.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "Admin viewed");
        auditLogService.log(AuditAction.DMS_SUSPENDED, AuditResult.SUCCESS,
                admin.getId(), admin.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "ownerId=" + owner.getId());
        auditLogService.log(AuditAction.DMS_RESUMED, AuditResult.SUCCESS,
                admin.getId(), admin.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "ownerId=" + owner.getId());

        Page<VaultActivityLogResponse> result = vaultActivityLogService.getVaultActivityLogs(
                vault.getId(), owner.getId(), null, null, PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(VaultActivityLogResponse::getAction)
                .containsExactly(AuditAction.VAULT_UPDATED)
                .doesNotContain(AuditAction.ADMIN_VAULT_VIEWED, AuditAction.DMS_SUSPENDED, AuditAction.DMS_RESUMED);

        // countQuery bị ẩn → totalElements = 1, không phải 4
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    private User saveUser(String prefix) {
        User u = userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .fullName(prefix)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(u.getId());
        return u;
    }
}
