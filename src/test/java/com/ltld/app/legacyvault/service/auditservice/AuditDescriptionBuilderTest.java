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
                Arguments.of(AuditAction.DMS_CONFIGURED, "Saved the Dead Man's Switch configuration."),
                Arguments.of(AuditAction.DMS_CHECKIN, "Checked in to confirm still active."),
                Arguments.of(AuditAction.DMS_TRIGGERED, "The Dead Man's Switch was triggered because the check-in deadline passed."),
                Arguments.of(AuditAction.DMS_TRIGGERED_NO_EXECUTOR,
                        "The Dead Man's Switch was triggered but no executor is available."),
                Arguments.of(AuditAction.DMS_SUSPENDED, "An administrator suspended the Dead Man's Switch."),
                Arguments.of(AuditAction.DMS_RESUMED,
                        "An administrator resumed the Dead Man's Switch and started a new check-in cycle."),
                Arguments.of(AuditAction.EXECUTOR_ASSIGNED, "Assigned an executor."),
                Arguments.of(AuditAction.EXECUTOR_REPLACED, "Changed the executor."),
                Arguments.of(AuditAction.EXECUTOR_ACTIVATED,
                        "Activated the executor after the Dead Man's Switch was triggered."),
                Arguments.of(AuditAction.EXECUTOR_ACKNOWLEDGED, "The executor acknowledged the activation notice.")
        );
    }

    @ParameterizedTest
    @MethodSource("dmsAndExecutorDescriptions")
    void describe_dmsAndExecutorActions_haveVietnameseSentence(AuditAction action, String expected) {
        String description = builder.describe(log(action, owner(), Map.of("result", "SUCCESS")));

        assertThat(description)
                .isEqualTo(expected)
                .doesNotStartWith("Performed action");
    }

    @Test
    void describe_executorRevokeByOwner_saysOwnerRevoked() {
        String description = builder.describe(log(AuditAction.EXECUTOR_REVOKE, owner(),
                Map.of("result", "SUCCESS", "detail", "ownerId=" + ownerId + ", reason=OWNER")));

        assertThat(description).isEqualTo("Revoked the executor.");
    }

    @Test
    void describe_executorRevokeBySystem_saysAutomaticRevoke() {
        String description = builder.describe(log(AuditAction.EXECUTOR_REVOKE, null,
                Map.of("result", "SUCCESS", "detail", "ownerId=" + ownerId + ", reason=BR03_TIMEOUT")));

        assertThat(description)
                .isEqualTo("The system automatically revoked the primary executor for not acknowledging in time.");
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
                .isEqualTo("Created a new vault.");
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
        assertThat(response.getActorName()).isEqualTo("Executor: Nguyen Van A");
        assertThat(response.getIpAddress()).isEqualTo("***.***.***.***");
        assertThat(response.getCreatedAt()).isEqualTo(acknowledged.getCreatedAt());
    }

    @Test
    void toResponse_ownerActor_seesSelfAndFullIp() {
        VaultActivityLogResponse response = builder.toResponse(
                log(AuditAction.DMS_CHECKIN, owner(), Map.of("result", "SUCCESS")), ownerId);

        assertThat(response.getActorName()).isEqualTo("You");
        assertThat(response.getIpAddress()).isEqualTo("192.168.1.100");
    }
}
