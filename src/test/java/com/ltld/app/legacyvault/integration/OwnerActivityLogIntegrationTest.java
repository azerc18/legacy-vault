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
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.auditservice.OwnerActivityLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
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

import java.time.LocalDateTime;
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.dms.scheduler.enabled=false"
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

        // Owner tự làm -> "Bạn"; BR-03 tự thu hồi (actor null) -> "Hệ thống"
        assertThat(logsA.getContent())
                .filteredOn(r -> r.getAction() == AuditAction.EXECUTOR_REVOKE)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getActorName()).isEqualTo("Hệ thống");
                    assertThat(r.getDescription()).isEqualTo(
                            "Hệ thống tự động thu hồi người thi hành chính do không xác nhận đúng hạn.");
                });
        assertThat(logsA.getContent())
                .filteredOn(r -> r.getAction() == AuditAction.DMS_CHECKIN)
                .singleElement()
                .satisfies(r -> assertThat(r.getActorName()).isEqualTo("Bạn"));
    }

    @Test
    void suspendByAdmin_appearsInOwnerLog_withAdminActorAndDescription() {
        Fixture a = fixture("s");
        User admin = user("admin-" + UUID.randomUUID().toString().substring(0, 8));
        admin.getRoles().add(roleRepository.findByCode("ADMIN").orElseThrow());
        userRepository.save(admin);

        // O5 qua service thật: ghi DMS_SUSPENDED với entityId = id DmsConfig, detail có reason
        dmsConfigService.suspendByAdmin(a.ownerId, admin.getId(), "Owner yêu cầu tạm dừng");

        Page<VaultActivityLogResponse> logs = ownerActivityLogService.getOwnerActivityLogs(
                a.ownerId, null, null, PageRequest.of(0, 50));

        assertThat(logs.getContent())
                .filteredOn(r -> r.getAction() == AuditAction.DMS_SUSPENDED)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getActorName()).isEqualTo("Quản trị viên hệ thống");
                    assertThat(r.getDescription()).isEqualTo("Quản trị viên tạm dừng Dead Man's Switch.");
                });
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

        // B-01a: phải có Primary trước khi bật DMS. Lưu qua repository nên không sinh audit.
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
