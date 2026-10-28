package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditDescriptionBuilder;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.auditservice.OwnerActivityLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-05 (O4): nhật ký cấp Owner trên MySQL thật. DMS_CONFIGURED / DMS_CHECKIN ghi qua DmsConfigService thật;
 * các sự kiện Executor và log Vault ghi qua AuditLogService với đúng tham số như nơi ghi thật.
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
class OwnerActivityLogIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @MockitoBean
    private EmailSender emailSender;                                     // không gửi mail thật
    @MockitoBean
    private ExecutorNotificationService executorNotificationService;   // không gửi mail thật

    @Autowired private OwnerActivityLogService ownerActivityLogService;
    @Autowired private DmsConfigService dmsConfigService;
    @Autowired private VaultService vaultService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private DmsCheckinRepository dmsCheckinRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Dữ liệu test tạo ra, để @AfterEach chỉ xóa đúng phần của mình.
    private final List<UUID> ownerIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID ownerId : ownerIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                    "%ownerId=" + ownerId + "%");
            dmsConfigRepository.findByOwnerId(ownerId).ifPresent(c -> {
                dmsCheckinRepository.deleteAll(dmsCheckinRepository.findAllByDmsConfigIdOrderByOccurredAtDesc(c.getId()));
                dmsConfigRepository.delete(c);
            });
            executorAssignmentRepository.deleteAll(executorAssignmentRepository.findAllByOwnerId(ownerId));
            vaultRepository.deleteAll(vaultRepository.findAll().stream()
                    .filter(v -> v.getOwner().getId().equals(ownerId))
                    .toList());
        }
        // VAULT_DELETED / ADMIN_VAULT_VIEWED không có ownerId trong detail: xóa theo actor, nếu không FK actor_id chặn xóa user
        for (UUID userId : userIds) {
            jdbcTemplate.update("DELETE FROM audit_logs WHERE BIN_TO_UUID(actor_id) = ?", userId.toString());
        }
        userRepository.deleteAllById(userIds);
    }

    @Test
    void owner_seesOnlyOwnDmsAndExecutorLogs_notOtherOwners_norVaultLogs() {
        Fixture a = fixture("a");
        Fixture b = fixture("b");

        Page<VaultActivityLogResponse> logsA = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 50));
        Page<VaultActivityLogResponse> logsB = ownerActivityLogService.getOwnerActivityLogs(
                b.ownerId, null, null, PageRequest.of(0, 50));

        // Mỗi Owner: DMS_CONFIGURED, DMS_CHECKIN, EXECUTOR_ASSIGNED (Primary), EXECUTOR_REVOKE (Backup đã REVOKED)
        assertThat(logsA.getContent()).extracting(VaultActivityLogResponse::getId)
                .containsExactlyInAnyOrderElementsOf(a.ownerLogIds())
                .doesNotContainAnyElementsOf(b.allLogIds());
        assertThat(logsB.getContent()).extracting(VaultActivityLogResponse::getId)
                .containsExactlyInAnyOrderElementsOf(b.ownerLogIds())
                .doesNotContainAnyElementsOf(a.allLogIds());

        // Log Vault (có vaultId) không lẫn vào nhật ký Owner, kể cả khi trùng entityType/entityId
        assertThat(logsA.getContent()).extracting(VaultActivityLogResponse::getAction)
                .containsExactlyInAnyOrder(AuditAction.DMS_CONFIGURED, AuditAction.DMS_CHECKIN,
                        AuditAction.EXECUTOR_ASSIGNED, AuditAction.EXECUTOR_REVOKE)
                .doesNotContain(AuditAction.VAULT_CREATED);

        // Mới nhất trước
        assertThat(logsA.getContent()).extracting(VaultActivityLogResponse::getCreatedAt)
                .isSortedAccordingTo(Comparator.reverseOrder());

        // Owner tự làm -> "You"; BR-03 tự thu hồi (actor null) -> "System"
        assertThat(logsA.getContent())
                .filteredOn(r -> r.getAction() == AuditAction.EXECUTOR_REVOKE)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getActorName()).isEqualTo("System");
                    assertThat(r.getDescription()).isEqualTo(
                            "The system automatically revoked the primary executor for not acknowledging in time.");
                });
        assertThat(logsA.getContent())
                .filteredOn(r -> r.getAction() == AuditAction.DMS_CHECKIN)
                .singleElement()
                .satisfies(r -> assertThat(r.getActorName()).isEqualTo("You"));
    }

    // FR-05: thay test cũ suspendByAdmin_appearsInOwnerLog_... (DMS_SUSPENDED nay bị ẩn khỏi nhật ký Owner)
    @Test
    void adminOnlyActions_hiddenFromOwnerLog_andNotCounted() {
        Fixture a = fixture("s");
        User admin = admin();
        Vault vault = vault(a.ownerId, "viewed");

        // Qua service thật: O5 ghi DMS_SUSPENDED / DMS_RESUMED (entity DmsConfig, vaultId null),
        // FR-20 ghi ADMIN_VAULT_VIEWED (vaultId của Owner, actor Admin)
        dmsConfigService.suspendByAdmin(a.ownerId, admin.getId(), "Owner yêu cầu tạm dừng");
        dmsConfigService.resumeByAdmin(a.ownerId, admin.getId());
        vaultService.getAdminVaultDetail(vault.getId(), admin.getId());
        Integer hiddenRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action IN ('DMS_SUSPENDED', 'DMS_RESUMED', 'ADMIN_VAULT_VIEWED')"
                        + " AND (JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ? OR BIN_TO_UUID(vault_id) = ?)",
                Integer.class, "%ownerId=" + a.ownerId + "%", vault.getId().toString());
        assertThat(hiddenRows).as("đã ghi đủ 3 log bị ẩn").isEqualTo(3);

        Page<VaultActivityLogResponse> logs = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 50));
        // Trang nhỏ hơn tổng số dòng -> Spring Data phải chạy countQuery để tính totalElements
        Page<VaultActivityLogResponse> firstOfOne = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 1));

        assertThat(logs.getContent()).extracting(VaultActivityLogResponse::getId)
                .containsExactlyInAnyOrderElementsOf(a.ownerLogIds());
        assertThat(logs.getContent()).extracting(VaultActivityLogResponse::getAction)
                .doesNotContainAnyElementsOf(AuditDescriptionBuilder.OWNER_HIDDEN_ACTIONS);
        assertThat(logs.getTotalElements()).isEqualTo(a.ownerLogIds().size());
        assertThat(firstOfOne.getTotalElements()).as("countQuery không được đếm action bị ẩn")
                .isEqualTo(a.ownerLogIds().size());
    }

    // FR-05: Owner thấy VAULT_DELETED do chính mình xóa; không thấy của Owner khác hay của người khác (Admin)
    @Test
    void ownVaultDeleted_appearsInOwnerLog_otherOwnersAndAdminDoNot() throws Exception {
        Fixture a = fixture("d");
        Fixture b = fixture("e");
        User admin = admin();
        Vault vaultA = vault(a.ownerId, "deleted-a");
        Vault vaultB = vault(b.ownerId, "deleted-b");
        Vault vaultAByAdmin = vault(a.ownerId, "admin-a");

        // Qua service thật: VAULT_DELETED có actor = Owner, vaultId = id Vault, entity "Vault", detail null
        vaultService.deleteVault(vaultA.getId(), a.ownerId);
        vaultService.deleteVault(vaultB.getId(), b.ownerId);
        // Phòng thủ: hiện chưa có luồng Admin xóa Vault; giả lập log cùng tham số với deleteVault nhưng actor là Admin
        auditLogService.log(AuditAction.VAULT_DELETED, AuditResult.SUCCESS, admin.getId(), admin.getEmail(),
                vaultAByAdmin.getId(), "Vault", vaultAByAdmin.getId().toString(), null);
        UUID deletedByA = vaultDeletedLogId(a.ownerId);
        UUID deletedByB = vaultDeletedLogId(b.ownerId);
        UUID deletedByAdmin = vaultDeletedLogId(admin.getId());

        Page<VaultActivityLogResponse> logsA = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 50));
        Page<VaultActivityLogResponse> logsB = ownerActivityLogService.getOwnerActivityLogs(
                b.ownerId, null, null, PageRequest.of(0, 50));
        Page<VaultActivityLogResponse> firstOfOneA = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 1));

        assertThat(logsA.getContent()).extracting(VaultActivityLogResponse::getId)
                .contains(deletedByA)
                .doesNotContain(deletedByB, deletedByAdmin);
        assertThat(logsA.getContent())
                .filteredOn(r -> r.getAction() == AuditAction.VAULT_DELETED)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getId()).isEqualTo(deletedByA);
                    assertThat(r.getActorName()).isEqualTo("You");
                    assertThat(r.getDescription()).isEqualTo("Deleted the vault.");
                });
        assertThat(logsA.getTotalElements()).isEqualTo(a.ownerLogIds().size() + 1);
        assertThat(firstOfOneA.getTotalElements()).isEqualTo(a.ownerLogIds().size() + 1);

        assertThat(logsB.getContent()).extracting(VaultActivityLogResponse::getId)
                .contains(deletedByB)
                .doesNotContain(deletedByA, deletedByAdmin);
    }

    // FR-05: không truyền from -> log cũ hơn 30 ngày (và hơn 1 năm) vẫn được trả về
    @Test
    void noFromDate_returnsLogsOlderThan30Days() {
        Fixture a = fixture("o");
        UUID configured = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM audit_logs WHERE action = 'DMS_CONFIGURED'"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?",
                String.class, "%ownerId=" + a.ownerId + "%"));
        jdbcTemplate.update("UPDATE audit_logs SET created_at = ? WHERE BIN_TO_UUID(id) = ?",
                Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS)), configured.toString());

        Page<VaultActivityLogResponse> logs = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 50));

        assertThat(logs.getContent())
                .filteredOn(r -> r.getId().equals(configured))
                .singleElement()
                .satisfies(r -> assertThat(r.getCreatedAt()).isBefore(Instant.now().minus(30, ChronoUnit.DAYS)));
        assertThat(logs.getTotalElements()).isEqualTo(a.ownerLogIds().size());
    }

    @Test
    void ownerLogs_paginateWithCorrectTotal() {
        Fixture a = fixture("p");

        Page<VaultActivityLogResponse> first = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 1));
        Page<VaultActivityLogResponse> second = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(1, 1));

        assertThat(first.getTotalElements()).isEqualTo(a.ownerLogIds().size());
        assertThat(first.getContent()).hasSize(1);
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.getContent().get(0).getId()).isNotEqualTo(first.getContent().get(0).getId());
    }

    @Test
    void ownerWithoutDmsOrExecutor_getsEmptyPage() {
        User owner = user("owner-c-" + UUID.randomUUID().toString().substring(0, 8));
        ownerIds.add(owner.getId());

        Page<VaultActivityLogResponse> result = ownerActivityLogService.getOwnerActivityLogs(
                owner.getId(), null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ---------- dữ liệu ----------

    private record Fixture(UUID ownerId, List<UUID> ownerLogIds, List<UUID> vaultLogIds) {
        List<UUID> allLogIds() {
            List<UUID> ids = new ArrayList<>(ownerLogIds);
            ids.addAll(vaultLogIds);
            return ids;
        }
    }

    private Fixture fixture(String name) {
        String tag = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        User owner = user("owner-" + tag);
        ownerIds.add(owner.getId());
        Vault vault = vaultRepository.save(Vault.builder().owner(owner).name("Vault " + tag).build());
        String ownerDetail = "ownerId=" + owner.getId();

        // FR-03: phải có Primary trước khi bật DMS. Lưu qua repository nên không sinh audit.
        ExecutorAssignment primary = assignment(owner, user("primary-" + tag), ExecutorType.PRIMARY, ExecutorStatus.INACTIVE);
        ExecutorAssignment revokedBackup = assignment(owner, user("backup-" + tag), ExecutorType.BACKUP, ExecutorStatus.REVOKED);

        // FR-03 qua service thật: ghi DMS_CONFIGURED và DMS_CHECKIN với entityId = id DmsConfig
        dmsConfigService.upsertConfig(owner.getId(), dmsRequest());
        dmsConfigService.checkIn(owner.getId());
        UUID configId = dmsConfigRepository.findByOwnerId(owner.getId()).orElseThrow().getId();

        // Cùng tham số như ExecutorManagementServiceImpl.audit và DmsActivationProcessor (BR-03, actor null)
        auditLogService.log(AuditAction.EXECUTOR_ASSIGNED, AuditResult.SUCCESS, owner.getId(), owner.getEmail(), null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, primary.getId().toString(),
                ownerDetail + ", executorType=PRIMARY");
        auditLogService.log(AuditAction.EXECUTOR_REVOKE, AuditResult.SUCCESS, null, null, null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, revokedBackup.getId().toString(),
                ownerDetail + ", executorType=BACKUP, reason=BR03_TIMEOUT");

        // Log Vault: một log thường, và một log có vaultId nhưng trùng entityType/entityId với DmsConfig
        auditLogService.log(AuditAction.VAULT_CREATED, AuditResult.SUCCESS, owner.getId(), owner.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), ownerDetail);
        auditLogService.log(AuditAction.VAULT_CREATED, AuditResult.SUCCESS, owner.getId(), owner.getEmail(), vault.getId(),
                DmsActivationProcessor.ENTITY_DMS_CONFIG, configId.toString(), ownerDetail);

        List<UUID> ownerLogIds = logIds(owner.getId(), false);
        List<UUID> vaultLogIds = logIds(owner.getId(), true);
        assertThat(ownerLogIds).hasSize(4);
        assertThat(vaultLogIds).hasSize(2);
        return new Fixture(owner.getId(), ownerLogIds, vaultLogIds);
    }

    private User admin() {
        User admin = user("admin-" + UUID.randomUUID().toString().substring(0, 8));
        admin.getRoles().add(roleRepository.findByCode("ADMIN").orElseThrow());
        return userRepository.save(admin);
    }

    private Vault vault(UUID ownerId, String name) {
        return vaultRepository.save(Vault.builder()
                .owner(userRepository.findById(ownerId).orElseThrow())
                .name("Vault " + name)
                .build());
    }

    // id log VAULT_DELETED do actor này ghi (detail null nên không tra theo ownerId trong detail được)
    private UUID vaultDeletedLogId(UUID actorId) {
        return UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM audit_logs WHERE action = 'VAULT_DELETED' AND BIN_TO_UUID(actor_id) = ?",
                String.class, actorId.toString()));
    }

    // id các audit log của Owner này, tách theo có / không có vault_id
    private List<UUID> logIds(UUID ownerId, boolean withVault) {
        return jdbcTemplate.queryForList(
                        "SELECT BIN_TO_UUID(id) FROM audit_logs WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.detail')) LIKE ?"
                                + (withVault ? " AND vault_id IS NOT NULL" : " AND vault_id IS NULL"),
                        String.class, "%ownerId=" + ownerId + "%")
                .stream().map(UUID::fromString).toList();
    }

    private User user(String name) {
        User user = userRepository.save(User.builder()
                .email(name + "@example.com")
                .fullName(name)
                .passwordHash("not-used")
                .status(UserStatus.ACTIVE)
                .build());
        userIds.add(user.getId());
        return user;
    }

    private ExecutorAssignment assignment(User owner, User executor, ExecutorType type, ExecutorStatus status) {
        return executorAssignmentRepository.save(ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executor)
                .executorType(type)
                .status(status)
                .revokedAt(status == ExecutorStatus.REVOKED ? LocalDateTime.now() : null)
                .build());
    }

    private static DmsConfigRequest dmsRequest() {
        DmsConfigRequest request = new DmsConfigRequest();
        request.setCheckinIntervalDays(30);
        request.setGracePeriodDays(7);
        request.setReminderCount(3);
        return request;
    }
}
