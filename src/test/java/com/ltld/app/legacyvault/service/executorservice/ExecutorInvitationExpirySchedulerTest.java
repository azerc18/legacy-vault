package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Hạn trả lời lời mời: nhắc một lần khi sắp hết hạn, thu hồi khi quá hạn.
 * TransactionTemplate thật trên một PlatformTransactionManager giả: callback chạy thẳng, setRollbackOnly chỉ được ghi nhận.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorInvitationExpirySchedulerTest {

    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private ExecutorInvitationExpiryScheduler scheduler;
    private ExecutorAssignment assignment;
    private User executorUser;
    private Role executorRole;
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);

    @BeforeEach
    void setUp() {
        executorRole = Role.builder().id(UUID.randomUUID()).code("EXECUTOR").name("Digital Executor").build();
        executorUser = User.builder().id(UUID.randomUUID()).email("exec@example.test").fullName("Exec B")
                .status(UserStatus.ACTIVE).roles(new HashSet<>(List.of(executorRole))).build();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@example.test").fullName("Owner A")
                .status(UserStatus.ACTIVE).roles(new HashSet<>()).build();
        assignment = ExecutorAssignment.builder()
                .id(UUID.randomUUID()).owner(owner).executorUser(executorUser)
                .executorType(ExecutorType.PRIMARY).status(ExecutorStatus.INACTIVE)
                .responseStatus(ExecutorResponseStatus.PENDING)
                .assignedAt(now.minusDays(7)).invitationExpiresAt(now.minusMinutes(1))
                .build();

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(executorAssignmentRepository.findByIdForUpdate(assignment.getId())).thenReturn(Optional.of(assignment));
        when(executorAssignmentRepository.findExecutorUserIdById(assignment.getId()))
                .thenReturn(Optional.of(executorUser.getId()));
        when(executorAssignmentRepository.saveAndFlush(any(ExecutorAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByIdForUpdate(executorUser.getId())).thenReturn(Optional.of(executorUser));
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                eq(executorUser.getId()), eq(ExecutorStatus.REVOKED), eq(assignment.getId()))).thenReturn(false);

        scheduler = new ExecutorInvitationExpiryScheduler(executorAssignmentRepository, userRepository,
                new ExecutorRoleManager(roleRepository, executorAssignmentRepository),
                new ExecutorInvitationPolicy(7, 3), auditLogService, eventPublisher, transactionManager);
    }

    private void expiredIds(UUID... ids) {
        when(executorAssignmentRepository.findExpiredInvitationIds(eq(now), any(Pageable.class)))
                .thenReturn(List.of(ids));
    }

    private void remindIds(UUID... ids) {
        when(executorAssignmentRepository.findInvitationIdsToRemind(eq(now), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(ids));
    }

    @Test
    @DisplayName("Quá hạn: thu hồi (REVOKED + EXPIRED), gỡ role, ghi audit, phát event báo Owner")
    void expireOverdue_revokesAndNotifies() {
        expiredIds(assignment.getId());

        scheduler.expireOverdue(now);

        assertThat(assignment.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.EXPIRED);
        assertThat(assignment.getRevokedAt()).isEqualTo(now);
        assertThat(assignment.getRespondedAt()).isEqualTo(now);
        assertThat(executorUser.getRoles()).isEmpty();
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_INVITATION_EXPIRED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), anyString(), eq(assignment.getId().toString()), anyString());

        ArgumentCaptor<ExecutorInvitationTimeoutEvent> ev =
                ArgumentCaptor.forClass(ExecutorInvitationTimeoutEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().kind()).isEqualTo(ExecutorInvitationTimeoutEvent.Kind.EXPIRED);
        assertThat(ev.getValue().ownerEmail()).isEqualTo("owner@example.test");
        assertThat(ev.getValue().executorLabel()).isEqualTo("Exec B (exec@example.test)");
        assertThat(ev.getValue().days()).isEqualTo(7);
    }

    @Test
    @DisplayName("Người được mời vừa đồng ý trước khi job chạy tới: không thu hồi, không báo ai")
    void expireOverdue_alreadyAnswered_skips() {
        assignment.setResponseStatus(ExecutorResponseStatus.ACCEPTED);
        expiredIds(assignment.getId());

        scheduler.expireOverdue(now);

        assertThat(assignment.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        verify(executorAssignmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService, eventPublisher);
    }

    @Test
    @DisplayName("Owner vừa thu hồi: job bỏ qua, không ghi đè EXPIRED lên dòng đã REVOKED")
    void expireOverdue_alreadyRevoked_skips() {
        assignment.setStatus(ExecutorStatus.REVOKED);
        expiredIds(assignment.getId());

        scheduler.expireOverdue(now);

        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.PENDING);
        verifyNoInteractions(auditLogService, eventPublisher);
    }

    @Test
    @DisplayName("Người đó còn làm Executor cho Owner khác: giữ role EXECUTOR")
    void expireOverdue_stillExecutorElsewhere_keepsRole() {
        expiredIds(assignment.getId());
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                eq(executorUser.getId()), eq(ExecutorStatus.REVOKED), eq(assignment.getId()))).thenReturn(true);

        scheduler.expireOverdue(now);

        assertThat(executorUser.getRoles()).containsExactly(executorRole);
    }

    @Test
    @DisplayName("Sắp hết hạn: gửi nhắc một lần, ghi mốc đã nhắc, không đụng trạng thái lời mời")
    void remindSoonToExpire_sendsOnce() {
        assignment.setInvitationExpiresAt(now.plusDays(2).plusHours(1));
        remindIds(assignment.getId());

        scheduler.remindSoonToExpire(now);

        assertThat(assignment.getInvitationReminderSentAt()).isEqualTo(now);
        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.PENDING);
        assertThat(assignment.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);

        ArgumentCaptor<ExecutorInvitationTimeoutEvent> ev =
                ArgumentCaptor.forClass(ExecutorInvitationTimeoutEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().kind()).isEqualTo(ExecutorInvitationTimeoutEvent.Kind.REMINDER);
        assertThat(ev.getValue().executorEmail()).isEqualTo("exec@example.test");
        // 49 giờ còn lại -> làm tròn lên 3 ngày
        assertThat(ev.getValue().days()).isEqualTo(3);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Đã nhắc rồi thì không nhắc lại")
    void remindSoonToExpire_alreadyReminded_skips() {
        assignment.setInvitationExpiresAt(now.plusDays(2));
        assignment.setInvitationReminderSentAt(now.minusDays(1));
        remindIds(assignment.getId());

        scheduler.remindSoonToExpire(now);

        assertThat(assignment.getInvitationReminderSentAt()).isEqualTo(now.minusDays(1));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Một dòng lỗi không chặn dòng còn lại")
    void expireOverdue_oneFailure_continues() {
        UUID broken = UUID.randomUUID();
        when(executorAssignmentRepository.findExecutorUserIdById(broken)).thenThrow(new IllegalStateException("db down"));
        expiredIds(broken, assignment.getId());

        scheduler.expireOverdue(now);

        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.EXPIRED);
    }

    @Test
    @DisplayName("Owner vừa thay người khác vào vị trí trước khi job khóa được dòng: bỏ qua")
    void expireOverdue_someoneElseTookTheSeat_skips() {
        expiredIds(assignment.getId());
        User other = User.builder().id(UUID.randomUUID()).email("other@example.test").fullName("Other")
                .status(UserStatus.ACTIVE).roles(new HashSet<>()).build();
        // Khóa theo người cũ, nhưng dòng đọc lại (đã khóa) đang mang người mới
        assignment.setExecutorUser(other);

        scheduler.expireOverdue(now);

        assertThat(assignment.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        assertThat(assignment.getResponseStatus()).isEqualTo(ExecutorResponseStatus.PENDING);
        verifyNoInteractions(auditLogService, eventPublisher);
    }

    @Test
    @DisplayName("scan() chạy cả hai lượt: nhắc rồi mới thu hồi")
    void scan_runsBothPasses() {
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);

        scheduler.scan();

        verify(executorAssignmentRepository).findInvitationIdsToRemind(any(), any(), any(Pageable.class));
        verify(executorAssignmentRepository).findExpiredInvitationIds(any(), any(Pageable.class));
    }
}
