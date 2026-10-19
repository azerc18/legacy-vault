package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDescriptionBuilderTest {

    private final AuditDescriptionBuilder builder = new AuditDescriptionBuilder();
    private final UUID ownerId = UUID.randomUUID();

    private User owner() {
        User owner = new User();
        owner.setId(ownerId);
        owner.setEmail("owner@example.com");
        return owner;
    }

    private AuditLog log(AuditAction action, User actor, Map<String, Object> metadata) {
        return AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(actor)
                .action(action)
                .metadata(metadata)
                .ipAddress("192.168.1.100")
                .createdAt(Instant.now())
                .build();
    }

    static Stream<Arguments> dmsAndExecutorDescriptions() {
        return Stream.of(
                Arguments.of(AuditAction.DMS_CONFIGURED, "Lưu cấu hình Dead Man's Switch."),
                Arguments.of(AuditAction.DMS_CHECKIN, "Check-in xác nhận vẫn còn hoạt động."),
                Arguments.of(AuditAction.DMS_TRIGGERED, "Dead Man's Switch được kích hoạt do quá hạn check-in."),
                Arguments.of(AuditAction.DMS_TRIGGERED_NO_EXECUTOR,
                        "Dead Man's Switch được kích hoạt nhưng không có người thi hành khả dụng."),
                Arguments.of(AuditAction.EXECUTOR_ASSIGNED, "Chỉ định người thi hành."),
                Arguments.of(AuditAction.EXECUTOR_REPLACED, "Thay đổi người thi hành."),
                Arguments.of(AuditAction.EXECUTOR_ACTIVATED,
                        "Kích hoạt người thi hành sau khi Dead Man's Switch được kích hoạt."),
                Arguments.of(AuditAction.EXECUTOR_ACKNOWLEDGED, "Người thi hành đã xác nhận nhận thông báo kích hoạt.")
        );
    }

    @ParameterizedTest
    @MethodSource("dmsAndExecutorDescriptions")
    void describe_dmsAndExecutorActions_haveVietnameseSentence(AuditAction action, String expected) {
        String description = builder.describe(log(action, owner(), Map.of("result", "SUCCESS")));

        assertThat(description)
                .isEqualTo(expected)
                .doesNotStartWith("Thực hiện hành động");
    }

    @Test
    void describe_executorRevokeByOwner_saysOwnerRevoked() {
        String description = builder.describe(log(AuditAction.EXECUTOR_REVOKE, owner(),
                Map.of("result", "SUCCESS", "detail", "ownerId=" + ownerId + ", reason=OWNER")));

        assertThat(description).isEqualTo("Thu hồi người thi hành.");
    }

    @Test
    void describe_executorRevokeBySystem_saysAutomaticRevoke() {
        String description = builder.describe(log(AuditAction.EXECUTOR_REVOKE, null,
                Map.of("result", "SUCCESS", "detail", "ownerId=" + ownerId + ", reason=BR03_TIMEOUT")));

        assertThat(description)
                .isEqualTo("Hệ thống tự động thu hồi người thi hành chính do không xác nhận đúng hạn.");
    }

    @Test
    void describe_dmsActions_doNotLeakDetail() {
        String assignmentId = UUID.randomUUID().toString();
        String detail = "ownerId=" + ownerId + ", executorType=PRIMARY, executorUserId=" + assignmentId;

        String description = builder.describe(log(AuditAction.EXECUTOR_ASSIGNED, owner(),
                Map.of("result", "SUCCESS", "email", "owner@example.com", "detail", detail)));

        assertThat(description)
                .doesNotContain(ownerId.toString())
                .doesNotContain(assignmentId)
                .doesNotContain("owner@example.com");
    }

    @Test
    void describe_existingVaultSentence_unchanged() {
        assertThat(builder.describe(log(AuditAction.VAULT_CREATED, owner(), Map.of("result", "SUCCESS"))))
                .isEqualTo("Khởi tạo két sắt mới.");
    }

    @Test
    void toResponse_executorActor_isMaskedAndIpHidden() {
        User executor = new User();
        executor.setId(UUID.randomUUID());
        executor.setFullName("Nguyen Van A");
        Role role = new Role();
        role.setCode("EXECUTOR");
        executor.setRoles(Set.of(role));
        AuditLog acknowledged = log(AuditAction.EXECUTOR_ACKNOWLEDGED, executor, Map.of("result", "SUCCESS"));

        VaultActivityLogResponse response = builder.toResponse(acknowledged, ownerId);

        assertThat(response.getId()).isEqualTo(acknowledged.getId());
        assertThat(response.getAction()).isEqualTo(AuditAction.EXECUTOR_ACKNOWLEDGED);
        assertThat(response.getActorName()).isEqualTo("Người thi hành: Nguyen Van A");
        assertThat(response.getIpAddress()).isEqualTo("***.***.***.***");
        assertThat(response.getCreatedAt()).isEqualTo(acknowledged.getCreatedAt());
    }

    @Test
    void toResponse_ownerActor_seesSelfAndFullIp() {
        VaultActivityLogResponse response = builder.toResponse(
                log(AuditAction.DMS_CHECKIN, owner(), Map.of("result", "SUCCESS")), ownerId);

        assertThat(response.getActorName()).isEqualTo("Bạn");
        assertThat(response.getIpAddress()).isEqualTo("192.168.1.100");
    }
}
