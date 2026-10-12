package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorManagementServiceImplTest {

    private static final String OWNER_EMAIL = "owner@example.test";
    private static final String CANDIDATE_EMAIL = "exec@example.test";

    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ExecutorManagementServiceImpl service;

    private UUID ownerId;
    private User owner;
    private User candidate;
    private Role executorRole;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = user(ownerId, OWNER_EMAIL, UserStatus.ACTIVE);
        candidate = user(UUID.randomUUID(), CANDIDATE_EMAIL, UserStatus.ACTIVE);
        executorRole = Role.builder().id(UUID.randomUUID()).code("EXECUTOR").name("Digital Executor").build();

        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.empty());
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail(CANDIDATE_EMAIL)).thenReturn(Optional.of(candidate));
        when(roleRepository.findByCode("EXECUTOR")).thenReturn(Optional.of(executorRole));
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(eq(ownerId), any()))
                .thenReturn(Optional.empty());
        when(executorAssignmentRepository.saveAndFlush(any(ExecutorAssignment.class))).thenAnswer(inv -> {
            ExecutorAssignment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
    }

    // ---------- PUT: chỉ định / thay thế ----------

    @Test
    @DisplayName("Chưa có dòng: tạo mới INACTIVE, thêm role EXECUTOR, flush trước rồi mới audit EXECUTOR_ASSIGNED")
    void assign_newRow_createsInactiveAssignment() {
        ExecutorAssignmentResponse response = service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false));

        ArgumentCaptor<ExecutorAssignment> captor = ArgumentCaptor.forClass(ExecutorAssignment.class);
        InOrder order = inOrder(executorAssignmentRepository, auditLogService);
        order.verify(executorAssignmentRepository).saveAndFlush(captor.capture());
        order.verify(auditLogService).log(eq(AuditAction.EXECUTOR_ASSIGNED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq(OWNER_EMAIL), isNull(), eq("ExecutorAssignment"),
                eq(captor.getValue().getId().toString()), contains("executorUserId=" + candidate.getId()));

        ExecutorAssignment saved = captor.getValue();
        assertThat(saved.getOwner()).isSameAs(owner);
        assertThat(saved.getExecutorUser()).isSameAs(candidate);
        assertThat(saved.getExecutorType()).isEqualTo(ExecutorType.PRIMARY);
        assertThat(saved.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(saved.getAssignedAt()).isNotNull();
        assertThat(candidate.getRoles()).containsExactly(executorRole);

        assertThat(response.getAssignmentId()).isEqualTo(saved.getId());
        assertThat(response.getExecutorType()).isEqualTo(ExecutorType.PRIMARY);
        assertThat(response.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(response.getEmail()).isEqualTo(CANDIDATE_EMAIL);
        assertThat(response.getFullName()).isEqualTo("Full " + CANDIDATE_EMAIL);
        assertThat(response.getAssignedAt()).isEqualTo(saved.getAssignedAt());
    }

    @Test
    @DisplayName("Email được trim + lowercase trước khi tìm user")
    void assign_normalizesEmail() {
        service.assignExecutor(ownerId, ExecutorType.BACKUP, request("  Exec@Example.TEST ", false));

        verify(userRepository).findByEmail(CANDIDATE_EMAIL);
        verify(executorAssignmentRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("DmsConfig ACTIVE: cho phép, và đọc DmsConfig qua truy vấn khóa PESSIMISTIC_FORCE_INCREMENT")
    void assign_dmsActive_allowedAndLocksDmsConfig() {
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId))
                .thenReturn(Optional.of(dmsConfig(DmsStatus.ACTIVE)));

        service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false));

        // Thứ tự khóa giống trigger FR-07: DmsConfig trước, ExecutorAssignment sau.
        InOrder order = inOrder(dmsConfigRepository, executorAssignmentRepository);
        order.verify(dmsConfigRepository).findByOwnerIdForUpdate(ownerId);
        order.verify(executorAssignmentRepository).findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY);
        order.verify(executorAssignmentRepository).saveAndFlush(any());
        verify(dmsConfigRepository, never()).findByOwnerId(any());
    }

    @Test
    @DisplayName("DMS TRIGGERED: 409, không ghi gì, không audit")
    void assign_dmsTriggered_conflict() {
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId))
                .thenReturn(Optional.of(dmsConfig(DmsStatus.TRIGGERED)));

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false)),
                HttpStatus.CONFLICT);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
        assertThat(candidate.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("Email chưa có tài khoản: 422")
    void assign_unknownEmail_unprocessable() {
        when(userRepository.findByEmail("nobody@example.test")).thenReturn(Optional.empty());

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request("nobody@example.test", false)),
                HttpStatus.UNPROCESSABLE_ENTITY);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"PENDING", "LOCKED"})
    @DisplayName("Tài khoản chưa ACTIVE: 422, cùng thông báo với email chưa đăng ký")
    void assign_notActiveUser_unprocessable(UserStatus status) {
        candidate.setStatus(status);

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false)),
                HttpStatus.UNPROCESSABLE_ENTITY);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        assertThat(candidate.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("Tự chỉ định chính mình: 400")
    void assign_self_badRequest() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(OWNER_EMAIL, false)),
                HttpStatus.BAD_REQUEST);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Cùng người đang giữ vị trí: trả về như cũ, không ghi, không audit, không đổi role")
    void assign_samePerson_noOp() {
        ExecutorAssignment current = assignment(ExecutorType.PRIMARY, candidate, ExecutorStatus.INACTIVE);

        ExecutorAssignmentResponse response =
                service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, true));

        assertThat(response.getAssignmentId()).isEqualTo(current.getId());
        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService, roleRepository);
        assertThat(candidate.getRoles()).isEmpty();
    }

    @Test
    @DisplayName("Người được chọn đang giữ vị trí còn lại (chưa REVOKED): 409")
    void assign_candidateHoldsOtherPosition_conflict() {
        assignment(ExecutorType.BACKUP, candidate, ExecutorStatus.INACTIVE);

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false)),
                HttpStatus.CONFLICT);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Người được chọn từng giữ vị trí còn lại nhưng đã REVOKED: cho phép")
    void assign_candidateHeldOtherPositionButRevoked_allowed() {
        assignment(ExecutorType.BACKUP, candidate, ExecutorStatus.REVOKED);

        service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false));

        verify(executorAssignmentRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("Vị trí đã có người khác, thiếu confirmReplace: 409, giữ nguyên dòng cũ")
    void assign_occupiedWithoutConfirm_conflict() {
        User previous = user(UUID.randomUUID(), "old@example.test", UserStatus.ACTIVE);
        ExecutorAssignment current = assignment(ExecutorType.PRIMARY, previous, ExecutorStatus.INACTIVE);

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false)),
                HttpStatus.CONFLICT);

        assertThat(current.getExecutorUser()).isSameAs(previous);
        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Thay người có confirmReplace: dùng lại dòng, reset mốc thời gian, audit EXECUTOR_REPLACED kèm user cũ/mới, gỡ role người cũ")
    void assign_replaceWithConfirm_reusesRowAndRemovesPreviousRole() {
        User previous = user(UUID.randomUUID(), "old@example.test", UserStatus.ACTIVE);
        previous.getRoles().add(executorRole);
        ExecutorAssignment current = assignment(ExecutorType.PRIMARY, previous, ExecutorStatus.INACTIVE);
        current.setActivatedAt(LocalDateTime.now().minusDays(3));
        current.setAcknowledgedAt(LocalDateTime.now().minusDays(2));
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                previous.getId(), ExecutorStatus.REVOKED, current.getId())).thenReturn(false);

        service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, true));

        InOrder order = inOrder(executorAssignmentRepository, auditLogService);
        order.verify(executorAssignmentRepository).saveAndFlush(current);
        order.verify(auditLogService).log(eq(AuditAction.EXECUTOR_REPLACED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq(OWNER_EMAIL), isNull(), eq("ExecutorAssignment"), eq(current.getId().toString()),
                contains("executorUserId=" + candidate.getId() + ", previousExecutorUserId=" + previous.getId()));

        assertThat(current.getExecutorUser()).isSameAs(candidate);
        assertThat(current.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(current.getActivatedAt()).isNull();
        assertThat(current.getAcknowledgedAt()).isNull();
        assertThat(current.getRevokedAt()).isNull();
        assertThat(current.getAssignedAt()).isNotNull();
        assertThat(previous.getRoles()).isEmpty();
        assertThat(candidate.getRoles()).containsExactly(executorRole);
    }

    @Test
    @DisplayName("Thay người: người cũ còn assignment khác chưa REVOKED thì giữ role EXECUTOR")
    void assign_replace_previousStillAssignedElsewhere_keepsRole() {
        User previous = user(UUID.randomUUID(), "old@example.test", UserStatus.ACTIVE);
        previous.getRoles().add(executorRole);
        ExecutorAssignment current = assignment(ExecutorType.PRIMARY, previous, ExecutorStatus.INACTIVE);
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                previous.getId(), ExecutorStatus.REVOKED, current.getId())).thenReturn(true);

        service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, true));

        assertThat(previous.getRoles()).containsExactly(executorRole);
    }

    @Test
    @DisplayName("Dòng REVOKED: dùng lại dòng cho người mới, xóa activatedAt/acknowledgedAt/revokedAt, audit EXECUTOR_ASSIGNED, không cần confirmReplace")
    void assign_revokedRow_reusedAsNewAssignment() {
        User previous = user(UUID.randomUUID(), "old@example.test", UserStatus.ACTIVE);
        ExecutorAssignment current = assignment(ExecutorType.BACKUP, previous, ExecutorStatus.REVOKED);
        current.setActivatedAt(LocalDateTime.now().minusDays(20));
        current.setAcknowledgedAt(LocalDateTime.now().minusDays(19));
        current.setRevokedAt(LocalDateTime.now().minusDays(5));

        service.assignExecutor(ownerId, ExecutorType.BACKUP, request(CANDIDATE_EMAIL, false));

        InOrder order = inOrder(executorAssignmentRepository, auditLogService);
        order.verify(executorAssignmentRepository).saveAndFlush(current);
        order.verify(auditLogService).log(eq(AuditAction.EXECUTOR_ASSIGNED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq(OWNER_EMAIL), isNull(), eq("ExecutorAssignment"), eq(current.getId().toString()),
                anyString());

        assertThat(current.getExecutorUser()).isSameAs(candidate);
        assertThat(current.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(current.getActivatedAt()).isNull();
        assertThat(current.getAcknowledgedAt()).isNull();
        assertThat(current.getRevokedAt()).isNull();
        // Người cũ đã bị thu hồi từ trước: không phải "bị thay", không đụng tới role của họ.
        verify(executorAssignmentRepository, never()).existsByExecutorUserIdAndStatusNotAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("Người được chọn đã có role EXECUTOR: không thêm trùng, không tra RoleRepository")
    void assign_candidateAlreadyExecutor_roleNotDuplicated() {
        candidate.getRoles().add(executorRole);

        service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false));

        assertThat(candidate.getRoles()).containsExactly(executorRole);
        verifyNoInteractions(roleRepository);
    }

    @Test
    @DisplayName("Hai request cùng tạo dòng mới: DataIntegrityViolationException -> 409, không audit")
    void assign_concurrentInsert_conflict() {
        when(executorAssignmentRepository.saveAndFlush(any(ExecutorAssignment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_executor_assignment_owner_type"));

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false)),
                HttpStatus.CONFLICT);

        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Không tìm thấy tài khoản Owner: 404")
    void assign_ownerMissing_notFound() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertStatus(() -> service.assignExecutor(ownerId, ExecutorType.PRIMARY, request(CANDIDATE_EMAIL, false)),
                HttpStatus.NOT_FOUND);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
    }

    // ---------- DELETE: thu hồi ----------

    @Test
    @DisplayName("Thu hồi: REVOKED + revokedAt, gỡ role nếu không còn assignment, flush trước audit EXECUTOR_REVOKE reason=OWNER")
    void revoke_success() {
        candidate.getRoles().add(executorRole);
        ExecutorAssignment current = assignment(ExecutorType.BACKUP, candidate, ExecutorStatus.INACTIVE);
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                candidate.getId(), ExecutorStatus.REVOKED, current.getId())).thenReturn(false);

        ExecutorAssignmentResponse response = service.revokeExecutor(ownerId, ExecutorType.BACKUP);

        InOrder order = inOrder(dmsConfigRepository, executorAssignmentRepository, auditLogService);
        order.verify(dmsConfigRepository).findByOwnerIdForUpdate(ownerId);
        order.verify(executorAssignmentRepository).findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP);
        order.verify(executorAssignmentRepository).saveAndFlush(current);
        order.verify(auditLogService).log(eq(AuditAction.EXECUTOR_REVOKE), eq(AuditResult.SUCCESS),
                eq(ownerId), eq(OWNER_EMAIL), isNull(), eq("ExecutorAssignment"), eq(current.getId().toString()),
                contains("reason=OWNER"));

        assertThat(current.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(candidate.getRoles()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(response.getRevokedAt()).isEqualTo(current.getRevokedAt());
    }

    @Test
    @DisplayName("Thu hồi: user còn assignment khác chưa REVOKED (Owner khác) thì giữ role EXECUTOR")
    void revoke_userStillAssignedElsewhere_keepsRole() {
        candidate.getRoles().add(executorRole);
        ExecutorAssignment current = assignment(ExecutorType.PRIMARY, candidate, ExecutorStatus.INACTIVE);
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                candidate.getId(), ExecutorStatus.REVOKED, current.getId())).thenReturn(true);

        service.revokeExecutor(ownerId, ExecutorType.PRIMARY);

        assertThat(current.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(candidate.getRoles()).containsExactly(executorRole);
    }

    @Test
    @DisplayName("Thu hồi khi chưa có dòng: 404")
    void revoke_noRow_notFound() {
        assertStatus(() -> service.revokeExecutor(ownerId, ExecutorType.PRIMARY), HttpStatus.NOT_FOUND);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Thu hồi dòng đã REVOKED: 404")
    void revoke_alreadyRevoked_notFound() {
        assignment(ExecutorType.PRIMARY, candidate, ExecutorStatus.REVOKED);

        assertStatus(() -> service.revokeExecutor(ownerId, ExecutorType.PRIMARY), HttpStatus.NOT_FOUND);

        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Thu hồi khi DMS TRIGGERED: 409, giữ nguyên assignment")
    void revoke_dmsTriggered_conflict() {
        ExecutorAssignment current = assignment(ExecutorType.PRIMARY, candidate, ExecutorStatus.ACTIVE);
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId))
                .thenReturn(Optional.of(dmsConfig(DmsStatus.TRIGGERED)));

        assertStatus(() -> service.revokeExecutor(ownerId, ExecutorType.PRIMARY), HttpStatus.CONFLICT);

        assertThat(current.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    // ---------- GET: danh sách ----------

    @Test
    @DisplayName("Danh sách: trả PRIMARY trước BACKUP, gồm cả dòng REVOKED, không khóa DmsConfig, không audit")
    void list_returnsAllPositionsIncludingRevoked() {
        User backupUser = user(UUID.randomUUID(), "backup@example.test", UserStatus.ACTIVE);
        ExecutorAssignment backup = ExecutorAssignment.builder().id(UUID.randomUUID()).owner(owner)
                .executorUser(backupUser).executorType(ExecutorType.BACKUP).status(ExecutorStatus.REVOKED)
                .revokedAt(LocalDateTime.now().minusDays(1)).build();
        ExecutorAssignment primary = ExecutorAssignment.builder().id(UUID.randomUUID()).owner(owner)
                .executorUser(candidate).executorType(ExecutorType.PRIMARY).status(ExecutorStatus.INACTIVE)
                .assignedAt(LocalDateTime.now().minusDays(2)).build();
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(backup, primary));

        List<ExecutorAssignmentResponse> result = service.listExecutors(ownerId);

        assertThat(result).extracting(ExecutorAssignmentResponse::getExecutorType)
                .containsExactly(ExecutorType.PRIMARY, ExecutorType.BACKUP);
        assertThat(result.get(0).getEmail()).isEqualTo(CANDIDATE_EMAIL);
        assertThat(result.get(0).getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(result.get(0).getAssignedAt()).isEqualTo(primary.getAssignedAt());
        assertThat(result.get(1).getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(result.get(1).getRevokedAt()).isEqualTo(backup.getRevokedAt());
        verifyNoInteractions(dmsConfigRepository, auditLogService);
    }

    @Test
    @DisplayName("Danh sách: Owner chưa chỉ định ai thì trả danh sách rỗng")
    void list_noAssignments_empty() {
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of());

        assertThat(service.listExecutors(ownerId)).isEmpty();
    }

    // ---------- helpers ----------

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ExecutorManagementException.class)
                .satisfies(e -> assertThat(((ExecutorManagementException) e).getStatus()).isEqualTo(expected));
    }

    private static AssignExecutorRequest request(String email, boolean confirmReplace) {
        AssignExecutorRequest request = new AssignExecutorRequest();
        request.setEmail(email);
        request.setConfirmReplace(confirmReplace);
        return request;
    }

    private static User user(UUID id, String email, UserStatus status) {
        Set<Role> roles = new HashSet<>();
        return User.builder().id(id).email(email).fullName("Full " + email).status(status).roles(roles).build();
    }

    private DmsConfig dmsConfig(DmsStatus status) {
        return DmsConfig.builder().id(UUID.randomUUID()).owner(owner).status(status).version(0L).build();
    }

    /** Tạo assignment có sẵn cho Owner và stub repository trả về nó theo loại. */
    private ExecutorAssignment assignment(ExecutorType type, User executor, ExecutorStatus status) {
        ExecutorAssignment a = ExecutorAssignment.builder()
                .id(UUID.randomUUID()).owner(owner).executorUser(executor)
                .executorType(type).status(status).version(0L).build();
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, type)).thenReturn(Optional.of(a));
        return a;
    }
}
