package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** R1: logic role EXECUTOR dùng chung cho FR-06 và FR-07. */
@ExtendWith(MockitoExtension.class)
class ExecutorRoleManagerTest {

    @Mock private RoleRepository roleRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;

    @InjectMocks
    private ExecutorRoleManager executorRoleManager;

    private Role executorRole;
    private Role ownerRole;
    private User user;

    @BeforeEach
    void setUp() {
        executorRole = Role.builder().id(UUID.randomUUID()).code("EXECUTOR").name("Digital Executor").build();
        ownerRole = Role.builder().id(UUID.randomUUID()).code("OWNER").name("Owner").build();
        user = User.builder().id(UUID.randomUUID()).email("exec@example.test").roles(new HashSet<>()).build();
    }

    @Test
    @DisplayName("grant: user chưa có role EXECUTOR thì được thêm")
    void grant_addsRole() {
        when(roleRepository.findByCode("EXECUTOR")).thenReturn(Optional.of(executorRole));

        executorRoleManager.grant(user);

        assertThat(user.getRoles()).containsExactly(executorRole);
    }

    @Test
    @DisplayName("grant: idempotent, đã có role thì không tra RoleRepository và không thêm trùng")
    void grant_alreadyHasRole_noOp() {
        user.getRoles().add(executorRole);

        executorRoleManager.grant(user);

        assertThat(user.getRoles()).containsExactly(executorRole);
        verify(roleRepository, never()).findByCode(any());
    }

    @Test
    @DisplayName("grant: role EXECUTOR chưa được seed -> IllegalStateException")
    void grant_roleMissing_throws() {
        when(roleRepository.findByCode("EXECUTOR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executorRoleManager.grant(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Role not found: EXECUTOR");
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("revokeIfUnused: không còn assignment nào chưa REVOKED (ngoài dòng loại trừ) -> gỡ EXECUTOR, giữ role khác")
    void revokeIfUnused_noOtherAssignment_removesExecutorRoleOnly() {
        user.getRoles().addAll(Set.of(executorRole, ownerRole));
        UUID excludedId = UUID.randomUUID();
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                user.getId(), ExecutorStatus.REVOKED, excludedId)).thenReturn(false);

        executorRoleManager.revokeIfUnused(user, excludedId);

        assertThat(user.getRoles()).containsExactly(ownerRole);
        verify(executorAssignmentRepository).existsByExecutorUserIdAndStatusNotAndIdNot(
                user.getId(), ExecutorStatus.REVOKED, excludedId);
    }

    @Test
    @DisplayName("revokeIfUnused: còn assignment khác chưa REVOKED -> giữ role EXECUTOR")
    void revokeIfUnused_stillAssignedElsewhere_keepsRole() {
        user.getRoles().add(executorRole);
        UUID excludedId = UUID.randomUUID();
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                user.getId(), ExecutorStatus.REVOKED, excludedId)).thenReturn(true);

        executorRoleManager.revokeIfUnused(user, excludedId);

        assertThat(user.getRoles()).containsExactly(executorRole);
    }

    @Test
    @DisplayName("revokeIfUnused: excludedAssignmentId null -> NullPointerException, không truy vấn, giữ nguyên roles")
    void revokeIfUnused_nullExcludedId_throwsNpe() {
        user.getRoles().add(executorRole);

        assertThatThrownBy(() -> executorRoleManager.revokeIfUnused(user, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("excludedAssignmentId");
        verifyNoInteractions(executorAssignmentRepository);
        assertThat(user.getRoles()).containsExactly(executorRole);
    }
}
