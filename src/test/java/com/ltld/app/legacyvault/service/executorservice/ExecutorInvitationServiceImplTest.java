package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorInvitationResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Lời mời làm Executor: người được chỉ định tự đồng ý / từ chối. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorInvitationServiceImplTest {

    private static final String OWNER_EMAIL = "owner@example.test";
    private static final String EXEC_EMAIL = "exec@example.test";

    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ExecutorInvitationServiceImpl service;

    private UUID ownerId;
    private UUID execId;
    private User owner;
    private User executorUser;
    private ExecutorAssignment assignment;

    @BeforeEach
    void setUp() {
        // ExecutorRoleManager thật dựng từ mock, để kiểm tra việc gỡ role EXECUTOR khi từ chối.
        service = new ExecutorInvitationServiceImpl(executorAssignmentRepository, dmsConfigRepository, userRepository,
                new ExecutorRoleManager(roleRepository, executorAssignmentRepository), auditLogService, eventPublisher);

        ownerId = UUID.randomUUID();
        execId = UUID.randomUUID();
        owner = user(ownerId, OWNER_EMAIL);
        executorUser = user(execId, EXEC_EMAIL);
        Role executorRole = Role.builder().id(UUID.randomUUID()).code("EXECUTOR").name("Digital Executor").build();
        // User.roles là Set và có @Builder.Default, nên thêm trực tiếp vào tập mặc định
        executorUser.getRoles().add(executorRole);

        assignment = ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .executorUser(executorUser)
                .executorType(ExecutorType.PRIMARY)
                .status(ExecutorStatus.INACTIVE)
                .responseStatus(ExecutorResponseStatus.PENDING)
                .assignedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(userRepository.findById(execId)).thenReturn(Optional.of(executorUser));
        when(userRepository.findByIdForUpdate(execId)).thenReturn(Optional.of(executorUser));
        when(userRepository.findByEmail(EXEC_EMAIL)).thenReturn(Optional.of(executorUser));
        when(roleRepository.findByCode("EXECUTOR")).thenReturn(Optional.of(executorRole));
        when(executorAssignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(executorAssignmentRepository.findOwnerIdById(assignment.getId())).thenReturn(Optional.of(ownerId));
        when(executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId)).thenReturn(List.of(assignment));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.empty());
        when(executorAssignmentRepository.saveAndFlush(any(ExecutorAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- getInvitation ----------

    @Test
    @DisplayName("Xem lời mời đang chờ -> canRespond = true, trả đúng Owner và vị trí")
    void getInvitation_pending_canRespond() {
        ExecutorInvitationResponse res = service.getInvitation(assignment.getId(), execId.toString());

        assertThat(res.getAssignmentId()).isEqualTo(assignment.getId());
        assertThat(res.getOwnerEmail()).isEqualTo(OWNER_EMAIL);
        assertThat(res.getExecutorType()).isEqualTo(ExecutorType.PRIMARY);
        assertThat(res.getResponseStatus()).isEqualTo(ExecutorResponseStatus.PENDING);
        assertThat(res.isCanRespond()).isTrue();
        // Owner chưa cấu hình DMS -> dùng mặc định BR-03 (14 ngày) cho câu mô tả trách nhiệm
        assertThat(res.getBackupActivationWaitDays())
                .isEqualTo(ExecutorInvitationServiceImpl.DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS);
    }

    @Test
    @DisplayName("Principal là email cũng xem được lời mời của chính mình")
    void getInvitation_byEmailPrincipal() {
        assertThat(service.getInvitation(assignment.getId(), EXEC_EMAIL).getAssignmentId())
                .isEqualTo(assignment.getId());
    }

    @Test
    @DisplayName("Lời mời của người khác -> 404 như không tồn tại")
    void getInvitation_otherUser_404() {
        UUID strangerId = UUID.randomUUID();
        when(userRepository.findById(strangerId))
                .thenReturn(Optional.of(user(strangerId, "stranger@example.test")));

        assertThatThrownBy(() -> service.getInvitation(assignment.getId(), strangerId.toString()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
    }

    // ---------- accept ----------

    @Test
    @DisplayName("Đồng ý -> ACCEPTED, giữ nguyên INACTIVE, ghi audit và phát sự kiện báo Owner")
    void accept_setsAcceptedAndNotifiesOwner() {
        ExecutorInvitationResponse res = service.accept(assignment.getId(), execId.toString());

        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.ACCEPTED);
        assertThat(assignment.getRespondedAt()).isNotNull();
        assertThat(assignment.getDeclineReason()).isNull();
        // Đồng ý KHÔNG kích hoạt: chỉ DMS trigger mới chuyển sang ACTIVE
        assertThat(assignment.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(res.isCanRespond()).isFalse();

        verify(auditLogService).log(eq(AuditAction.EXECUTOR_ACCEPTED), eq(AuditResult.SUCCESS), eq(execId),
                eq(EXEC_EMAIL), isNull(), anyString(), anyString(), anyString());
        ExecutorInvitationRespondedEvent event = publishedEvent();
        assertThat(event.accepted()).isTrue();
        assertThat(event.ownerId()).isEqualTo(ownerId);
        assertThat(event.ownerEmail()).isEqualTo(OWNER_EMAIL);
        assertThat(event.declineReason()).isNull();
    }

    @Test
    @DisplayName("Đồng ý lần hai -> 409 EXECUTOR_INVITATION_CLOSED, không ghi gì thêm")
    void accept_twice_conflict() {
        service.accept(assignment.getId(), execId.toString());

        assertThatThrownBy(() -> service.accept(assignment.getId(), execId.toString()))
                .isInstanceOf(ExecutorManagementException.class)
                .extracting(e -> ((ExecutorManagementException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXECUTOR_INVITATION_CLOSED);
    }

    @Test
    @DisplayName("Owner đã thu hồi lời mời -> 409, không đổi gì")
    void accept_revoked_conflict() {
        assignment.setStatus(ExecutorStatus.REVOKED);

        assertThatThrownBy(() -> service.accept(assignment.getId(), execId.toString()))
                .isInstanceOf(ExecutorManagementException.class);
        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.PENDING);
        verifyNoInteractions(auditLogService, eventPublisher);
    }

    @Test
    @DisplayName("DMS của Owner đã bắt đầu bàn giao -> 409, không trả lời được nữa")
    void accept_afterHandoverStarted_conflict() {
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId))
                .thenReturn(Optional.of(DmsConfig.builder().owner(owner).status(DmsStatus.TRIGGERED).build()));

        assertThatThrownBy(() -> service.accept(assignment.getId(), execId.toString()))
                .isInstanceOf(ExecutorManagementException.class)
                .extracting(e -> ((ExecutorManagementException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXECUTOR_INVITATION_CLOSED);
        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.PENDING);
    }

    // ---------- decline ----------

    @Test
    @DisplayName("Từ chối -> DECLINED + REVOKED, lưu lý do, gỡ role EXECUTOR, báo Owner")
    void decline_revokesAndNotifiesOwner() {
        ExecutorInvitationResponse res = service.decline(assignment.getId(), execId.toString(), "  Bận quá  ");

        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.DECLINED);
        // Giải phóng vị trí: mọi chỗ xét "!= REVOKED" tự bỏ qua, Owner chỉ định người khác được ngay
        assertThat(assignment.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(assignment.getRevokedAt()).isNotNull();
        assertThat(assignment.getDeclineReason()).isEqualTo("Bận quá");
        assertThat(res.isCanRespond()).isFalse();

        // Không còn làm Executor cho Owner nào khác -> gỡ role
        assertThat(executorUser.getRoles()).noneMatch(r -> "EXECUTOR".equals(r.getCode()));
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_DECLINED), eq(AuditResult.SUCCESS), eq(execId),
                eq(EXEC_EMAIL), isNull(), anyString(), anyString(), anyString());
        ExecutorInvitationRespondedEvent event = publishedEvent();
        assertThat(event.accepted()).isFalse();
        assertThat(event.declineReason()).isEqualTo("Bận quá");
    }

    @Test
    @DisplayName("Từ chối không nêu lý do -> declineReason = null")
    void decline_blankReason_storesNull() {
        service.decline(assignment.getId(), execId.toString(), "   ");

        assertThat(assignment.getDeclineReason()).isNull();
        assertThat(publishedEvent().declineReason()).isNull();
    }

    @Test
    @DisplayName("Còn làm Executor cho Owner khác -> từ chối lời mời này KHÔNG gỡ role EXECUTOR")
    void decline_stillExecutorElsewhere_keepsRole() {
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                execId, ExecutorStatus.REVOKED, assignment.getId())).thenReturn(true);

        service.decline(assignment.getId(), execId.toString(), null);

        assertThat(executorUser.getRoles()).anyMatch(r -> "EXECUTOR".equals(r.getCode()));
    }

    @ParameterizedTest
    @EnumSource(value = ExecutorResponseStatus.class, names = {"ACCEPTED", "DECLINED"})
    @DisplayName("Lời mời đã trả lời rồi -> từ chối cũng 409")
    void decline_alreadyAnswered_conflict(ExecutorResponseStatus already) {
        assignment.setResponseStatus(already);

        assertThatThrownBy(() -> service.decline(assignment.getId(), execId.toString(), null))
                .isInstanceOf(ExecutorManagementException.class)
                .extracting(e -> ((ExecutorManagementException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXECUTOR_INVITATION_CLOSED);
        verify(executorAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Owner vừa thay người trên cùng dòng -> lời mời không còn của người gọi, 404")
    void decline_afterOwnerReplacedSameRow_404() {
        assignment.setExecutorUser(user(UUID.randomUUID(), "newexec@example.test"));

        assertThatThrownBy(() -> service.decline(assignment.getId(), execId.toString(), null))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
    }

    // ---------- helpers ----------

    private ExecutorInvitationRespondedEvent publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ExecutorInvitationRespondedEvent.class);
        return (ExecutorInvitationRespondedEvent) captor.getValue();
    }

    private static User user(UUID id, String email) {
        return User.builder().id(id).email(email).fullName(email).status(UserStatus.ACTIVE).build();
    }
}
