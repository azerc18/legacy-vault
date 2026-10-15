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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultActivityLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private VaultRepository vaultRepository;

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
        when(auditLogRepository.findVaultActivity(eq(vaultId), any(), any(), eq(pageable)))
                .thenReturn(logPage);

        Page<VaultActivityLogResponse> result = vaultActivityLogService.getVaultActivityLogs(
                vaultId, ownerId, null, null, pageable);

        assertThat(result.getContent()).hasSize(4);

        // Owner: actorName = "Bạn", IP nguyên vẹn
        assertThat(result.getContent().get(0).getActorName()).isEqualTo("Bạn");
        assertThat(result.getContent().get(0).getIpAddress()).isEqualTo("192.168.1.100");

        // Admin: actorName = "Quản trị viên hệ thống", IP bị mask
        assertThat(result.getContent().get(1).getActorName()).isEqualTo("Quản trị viên hệ thống");
        assertThat(result.getContent().get(1).getIpAddress()).isEqualTo("***.***.***.***");

        // Executor: actorName = "Người thi hành: Nguyen Van A", IP bị mask
        assertThat(result.getContent().get(2).getActorName()).isEqualTo("Người thi hành: Nguyen Van A");
        assertThat(result.getContent().get(2).getIpAddress()).isEqualTo("***.***.***.***");

        // System: actorName = "Hệ thống"
        assertThat(result.getContent().get(3).getActorName()).isEqualTo("Hệ thống");
        assertThat(result.getContent().get(3).getIpAddress()).isNull();
    }

    @Test
    void getVaultActivityLogs_NotOwner_ThrowsForbidden() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> vaultActivityLogService.getVaultActivityLogs(
                vaultId, strangerId, null, null, pageable))
                .isInstanceOf(VaultException.class)
                .satisfies(ex -> assertThat(((VaultException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
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
    void getVaultActivityLogs_RangeExceedsOneYear_ThrowsBadRequest() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(vault));
        Instant to = Instant.now();
        Instant from = to.minus(400, ChronoUnit.DAYS);

        assertThatThrownBy(() -> vaultActivityLogService.getVaultActivityLogs(
                vaultId, ownerId, from, to, pageable))
                .isInstanceOf(VaultException.class)
                .satisfies(ex -> assertThat(((VaultException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
