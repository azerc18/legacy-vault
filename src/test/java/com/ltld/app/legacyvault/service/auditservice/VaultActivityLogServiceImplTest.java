package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultActivityLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private VaultRepository vaultRepository;

    @Spy
    private AuditDescriptionBuilder descriptionBuilder = new AuditDescriptionBuilder();

    @InjectMocks
    private VaultActivityLogServiceImpl vaultActivityLogService;

    private UUID vaultId;
    private UUID ownerId;
    private User owner;
    private Vault vault;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        vaultId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        owner = new User();
        owner.setId(ownerId);
        owner.setEmail("owner@example.com");

        vault = new Vault();
        vault.setId(vaultId);
        vault.setOwner(owner);

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getVaultActivityLogs_Success_MaskingRules() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));

        // 1. Log do chính owner thực hiện
        AuditLog ownerLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(owner)
                .action(AuditAction.VAULT_CREATED)
                .vaultId(vaultId)
                .ipAddress("192.168.1.100")
                .createdAt(Instant.now())
                .build();

        // 2. Log do Admin thực hiện
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminUser.setRoles(Set.of(adminRole));

        AuditLog adminLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(adminUser)
                .action(AuditAction.DOCUMENT_UPLOADED)
                .vaultId(vaultId)
                .ipAddress("10.0.0.1")
                .createdAt(Instant.now())
                .build();

        // 3. Log do Executor thực hiện
        User executorUser = new User();
        executorUser.setId(UUID.randomUUID());
        executorUser.setFullName("Nguyen Van A");
        Role execRole = new Role();
        execRole.setCode("EXECUTOR");
        executorUser.setRoles(Set.of(execRole));

        AuditLog execLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(executorUser)
                .action(AuditAction.ASSET_VIEWED)
                .vaultId(vaultId)
                .ipAddress("10.0.0.2")
                .createdAt(Instant.now())
                .build();

        // 4. Log do hệ thống thực hiện (actor == null)
        AuditLog systemLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(null)
                .action(AuditAction.VAULT_DELETED)
                .vaultId(vaultId)
                .createdAt(Instant.now())
                .build();

        Page<AuditLog> logPage = new PageImpl<>(List.of(ownerLog, adminLog, execLog, systemLog));
        when(auditLogRepository.findVaultActivity(eq(vaultId), any(), any(), any(), eq(pageable)))
                .thenReturn(logPage);

        Page<VaultActivityLogResponse> result = vaultActivityLogService.getVaultActivityLogs(
                vaultId, ownerId, null, null, pageable);

        verify(auditLogRepository).findVaultActivity(
                eq(vaultId),
                same(AuditDescriptionBuilder.OWNER_HIDDEN_ACTIONS),
                any(),
                any(),
                eq(pageable)
        );

        assertThat(result.getContent()).hasSize(4);

        // Owner: actorName = "Bạn", IP nguyên vẹn
        assertThat(result.getContent().get(0).getActorName()).isEqualTo("You");
        assertThat(result.getContent().get(0).getIpAddress()).isEqualTo("192.168.1.100");

        // Admin: actorName = "Quản trị viên hệ thống", IP bị mask
        assertThat(result.getContent().get(1).getActorName()).isEqualTo("System administrator");
        assertThat(result.getContent().get(1).getIpAddress()).isEqualTo("***.***.***.***");

        // Executor: actorName = "Người thi hành: Nguyen Van A", IP bị mask
        assertThat(result.getContent().get(2).getActorName()).isEqualTo("Executor: Nguyen Van A");
        assertThat(result.getContent().get(2).getIpAddress()).isEqualTo("***.***.***.***");

        // System: actorName = "Hệ thống"
        assertThat(result.getContent().get(3).getActorName()).isEqualTo("System");
        assertThat(result.getContent().get(3).getIpAddress()).isNull();
    }

    @Test
    void getVaultActivityLogs_NotOwner_ThrowsNotFound() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> vaultActivityLogService.getVaultActivityLogs(
                vaultId, strangerId, null, null, pageable))
                .isInstanceOf(VaultException.class)
                .satisfies(ex -> assertThat(((VaultException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getVaultActivityLogs_StartDateAfterEndDate_ThrowsBadRequest() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        Instant now = Instant.now();
        Instant past = now.minus(5, ChronoUnit.DAYS);

        assertThatThrownBy(() -> vaultActivityLogService.getVaultActivityLogs(
                vaultId, ownerId, now, past, pageable))
                .isInstanceOf(VaultException.class)
                .satisfies(ex -> assertThat(((VaultException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getVaultActivityLogs_RangeExceedsOneYear_Success() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        Instant to = Instant.now();
        Instant from = to.minus(400, ChronoUnit.DAYS);
        when(auditLogRepository.findVaultActivity(eq(vaultId), any(), any(), any(), eq(pageable)))
                .thenReturn(Page.empty());

        Page<VaultActivityLogResponse> result = vaultActivityLogService.getVaultActivityLogs(
                vaultId, ownerId, from, to, pageable);
        // n3: kiểm service truyền đúng from/to xuống repository (không bị clamp về 1 năm)
        verify(auditLogRepository).findVaultActivity(
                eq(vaultId),
                same(AuditDescriptionBuilder.OWNER_HIDDEN_ACTIONS),
                eq(from),
                eq(to),
                eq(pageable)
        );
        assertThat(result.getTotalElements()).isZero();
    }

    // Trả về description của một log duy nhất do owner thực hiện
    private String describe(AuditAction action, Map<String, Object> metadata) {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        AuditLog log = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(owner)
                .action(action)
                .vaultId(vaultId)
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();
        when(auditLogRepository.findVaultActivity(eq(vaultId), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));

        return vaultActivityLogService.getVaultActivityLogs(vaultId, ownerId, null, null, pageable)
                .getContent().get(0).getDescription();
    }

    static Stream<Arguments> newDescriptions() {
        return Stream.of(
                Arguments.of(AuditAction.BENEFICIARY_ASSIGNED, "Assigned a beneficiary to the vault."),
                Arguments.of(AuditAction.BENEFICIARY_REPLACED, "Changed the vault's beneficiary."),
                Arguments.of(AuditAction.BENEFICIARY_ASSIGN_DENIED, "Beneficiary assignment denied."),
                Arguments.of(AuditAction.BENEFICIARY_LINKED, "The beneficiary linked their account to the vault."),
                Arguments.of(AuditAction.VAULT_UNLOCK, "The vault was unlocked after legal verification."),
                Arguments.of(AuditAction.VAULT_ARCHIVED, "The vault was archived because the claim period expired."),
                Arguments.of(AuditAction.CLAIM_COMPLETED, "The beneficiary completed the handover."),
                Arguments.of(AuditAction.ASSET_VIEWED, "Viewed digital asset details."),
                Arguments.of(AuditAction.ASSET_DOWNLOADED, "Downloaded a digital asset."),
                Arguments.of(AuditAction.ASSET_CREATED, "Added a new digital asset."),
                Arguments.of(AuditAction.ASSET_UPDATED, "Updated a digital asset."),
                Arguments.of(AuditAction.ASSET_DELETED, "Deleted a digital asset."),
                Arguments.of(AuditAction.DOCUMENT_DOWNLOADED, "Downloaded a document."),
                Arguments.of(AuditAction.LEGAL_VERIFICATION_REQUESTED, "Submitted a legal verification request."),
                Arguments.of(AuditAction.BENEFICIARY_NOTIFIED, "Sent the handover notice to the beneficiary."),
                Arguments.of(AuditAction.BENEFICIARY_NOTIFY_FAILED, "Failed to notify the beneficiary."),
                Arguments.of(AuditAction.BENEFICIARY_NOTIFY_GAVE_UP,
                        "Stopped notifying the beneficiary after repeated failures."),
                Arguments.of(AuditAction.BENEFICIARY_PENDING_NOTIFIED,
                        "Sent a sign-up invitation to the beneficiary (no account yet)."),
                Arguments.of(AuditAction.IDENTITY_OTP_SENT, "Sent the beneficiary identity verification OTP."),
                Arguments.of(AuditAction.IDENTITY_VERIFIED, "The beneficiary verified their identity."),
                Arguments.of(AuditAction.IDENTITY_VERIFY_FAILED, "The beneficiary failed identity verification."),
                Arguments.of(AuditAction.IDENTITY_LOCKED, "Identity verification locked after too many failed attempts.")
        );
    }

    @ParameterizedTest
    @MethodSource("newDescriptions")
    void buildDescription_vaultActions_haveVietnameseSentence(AuditAction action, String expected) {
        String description = describe(action, Map.of("result", "SUCCESS"));

        assertThat(description)
                .isEqualTo(expected)
                .doesNotStartWith("Performed action");
    }

    @Test
    void buildDescription_beneficiaryAssignDenied_doesNotLeakDetail() {
        String beneficiaryUserId = UUID.randomUUID().toString();
        String detail = "beneficiaryUserId=" + beneficiaryUserId + ", vault status LOCKED";

        String description = describe(AuditAction.BENEFICIARY_ASSIGN_DENIED,
                Map.of("result", "FAILURE", "email", "owner@example.com", "detail", detail));

        assertThat(description)
                .isEqualTo("Beneficiary assignment denied.")
                .doesNotContain(detail)
                .doesNotContain(beneficiaryUserId)
                .doesNotContain("owner@example.com");
    }

    @Test
    void buildDescription_documentDownloadFailure_saysFailed() {
        String description = describe(AuditAction.DOCUMENT_DOWNLOADED,
                Map.of("result", "FAILURE", "detail", "Read or decrypt failed"));

        assertThat(description).isEqualTo("Document download failed.");
    }
}
