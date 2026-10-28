package com.ltld.app.legacyvault.integration;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class VaultQueryIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private VaultRepository vaultRepository;
    @Autowired private DmsConfigRepository dmsConfigRepository;
    @Autowired private DmsCheckinRepository dmsCheckinRepository;
    @Autowired private ExecutorAssignmentRepository executorAssignmentRepository;
    @Autowired private DmsConfigService dmsConfigService;
    @Autowired private VaultService vaultService;
    @Autowired private JdbcTemplate jdbcTemplate;

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

    // FR-27 (C1): xem danh sách / chi tiết không được khóa hay tăng version DmsConfig, và không 500 khi TRIGGERED
    @Test
    @DisplayName("C1: listAndDetail không tăng DmsConfig version và không 500 khi TRIGGERED")
    void listAndDetail_withDmsActiveThenTriggered_return200_andDoNotBumpDmsVersion() {
        User owner = user("c1-owner");
        ownerIds.add(owner.getId());

        User exec = user("c1-exec");
        assignment(owner, exec, ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);

        Vault vault = vaultRepository.save(Vault.builder()
                .owner(owner)
                .name("Vault C1")
                .status(VaultStatus.ACTIVE)
                .build());

        DmsConfigRequest req = dmsRequest();
        dmsConfigService.upsertConfig(owner.getId(), req);

        long v0 = dmsConfigRepository.findByOwnerId(owner.getId()).orElseThrow().getVersion();

        // 1. DMS ACTIVE: getMyVaults trả modifiable = true, không tăng version DmsConfig
        assertThat(vaultService.getMyVaults(owner.getId())).singleElement()
                .satisfies(r -> {
                    assertThat(r.isModifiable()).isTrue();
                    assertThat(r.getLockReason()).isNull();
                });
        assertThat(dmsConfigRepository.findByOwnerId(owner.getId()).orElseThrow().getVersion()).isEqualTo(v0);

        // getVaultDetail cũng trả modifiable = true, không tăng version DmsConfig
        assertThat(vaultService.getVaultDetail(vault.getId(), owner.getId()).isModifiable()).isTrue();
        assertThat(dmsConfigRepository.findByOwnerId(owner.getId()).orElseThrow().getVersion()).isEqualTo(v0);

        // 2. Chuyển DMS sang TRIGGERED bằng jdbcTemplate
        UUID configId = dmsConfigRepository.findByOwnerId(owner.getId()).orElseThrow().getId();
        jdbcTemplate.update("UPDATE dms_configs SET status = 'TRIGGERED' WHERE id = UUID_TO_BIN(?)", configId.toString());

        // getMyVaults và getVaultDetail trả 200 (không bị UnexpectedRollbackException 500)
        assertThat(vaultService.getMyVaults(owner.getId())).singleElement()
                .satisfies(r -> {
                    assertThat(r.isModifiable()).isFalse();
                    assertThat(r.getLockReason()).isEqualTo("The handover process has started.");
                });

        assertThat(vaultService.getVaultDetail(vault.getId(), owner.getId()).getLockReason())
                .isEqualTo("The handover process has started.");
        assertThat(vaultService.getVaultDetail(vault.getId(), owner.getId()).isModifiable()).isFalse();
    }

    // FR-27 (m1): thứ tự danh sách Vault phải sắp xếp mới nhất lên đầu (createdAt DESC, id DESC)
    @Test
    @DisplayName("m1: getMyVaults trả về danh sách sắp xếp mới nhất lên đầu (createdAt DESC)")
    void getMyVaults_returnsNewestFirst() {
        User owner = user("m1-owner");
        ownerIds.add(owner.getId());

        LocalDateTime now = LocalDateTime.now();

        Vault older = vaultRepository.save(Vault.builder()
                .owner(owner)
                .name("Older Vault")
                .status(VaultStatus.ACTIVE)
                .createdAt(now.minusHours(2))
                .build());

        Vault newer = vaultRepository.save(Vault.builder()
                .owner(owner)
                .name("Newer Vault")
                .status(VaultStatus.ACTIVE)
                .createdAt(now.minusHours(1))
                .build());

        List<VaultResponse> vaults = vaultService.getMyVaults(owner.getId());
        assertThat(vaults).hasSize(2);
        assertThat(vaults.get(0).getId()).isEqualTo(newer.getId());
        assertThat(vaults.get(0).getName()).isEqualTo("Newer Vault");
        assertThat(vaults.get(1).getId()).isEqualTo(older.getId());
        assertThat(vaults.get(1).getName()).isEqualTo("Older Vault");
    }

    private User user(String name) {
        User user = userRepository.save(User.builder()
                .email(name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
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
