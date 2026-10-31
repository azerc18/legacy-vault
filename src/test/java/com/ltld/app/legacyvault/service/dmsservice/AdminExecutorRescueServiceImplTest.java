package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.service.executorservice.ExecutorRoleManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminExecutorRescueServiceImplTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private ExecutorRoleManager executorRoleManager;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @InjectMocks private AdminExecutorRescueServiceImpl service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final User owner = User.builder().id(ownerId).email("owner@example.test").fullName("Owner A").build();

    private DmsConfig triggeredConfig() {
        return DmsConfig.builder().owner(owner).status(DmsStatus.TRIGGERED).backupActivationWaitDays(14).build();
    }

    private static User user(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).status(UserStatus.ACTIVE).build();
    }

    private ExecutorAssignment assignment(ExecutorType type, ExecutorStatus status, User u, LocalDateTime activatedAt) {
        return ExecutorAssignment.builder().id(UUID.randomUUID()).owner(owner).executorType(type)
                .executorUser(u).status(status).activatedAt(activatedAt).build();
    }

    // FR-07: predicate truyền đúng ownerId / executorId / activatedAt vào repository
    @Test
    void rescue_passesOwnerExecutorAndActivatedAtToRepository() {
        User exec = user("exec@example.test");
        LocalDateTime activatedAt = LocalDateTime.now().minusDays(20);
        ExecutorAssignment primary = assignment(ExecutorType.PRIMARY, ExecutorStatus.ACTIVE, exec, activatedAt);
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.of(triggeredConfig()));
        when(executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId)).thenReturn(List.of(primary));
        when(legalVerificationRequestRepository.existsByOwnerIdAndExecutorIdAndCreatedAtAfter(
                ownerId, exec.getId(), activatedAt)).thenReturn(true);   // đã nộp -> không bỏ dở -> từ chối

        assertThatThrownBy(() -> service.rescue(adminId, ownerId, "new@example.test"))
                .isInstanceOf(DmsException.class);

        verify(legalVerificationRequestRepository)
                .existsByOwnerIdAndExecutorIdAndCreatedAtAfter(ownerId, exec.getId(), activatedAt);
    }

    // FR-07: Backup đã được kích hoạt rồi bỏ dở -> bị thu hồi, chỉ còn một Executor ACTIVE
    @Test
    void rescue_revokesStalledActiveBackup_andLeavesOneActiveExecutor() {
        User oldPrimaryUser = user("oldprimary@example.test");
        User backupUser = user("backup@example.test");
        User candidate = user("new@example.test");
        ExecutorAssignment primary = assignment(ExecutorType.PRIMARY, ExecutorStatus.REVOKED, oldPrimaryUser,
                LocalDateTime.now().minusDays(40));
        ExecutorAssignment backup = assignment(ExecutorType.BACKUP, ExecutorStatus.ACTIVE, backupUser,
                LocalDateTime.now().minusDays(20));

        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.of(triggeredConfig()));
        when(executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId)).thenReturn(List.of(primary, backup));
        when(legalVerificationRequestRepository.existsByOwnerIdAndExecutorIdAndCreatedAtAfter(
                any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmail("new@example.test")).thenReturn(Optional.of(candidate));
        when(vaultRepository.existsBeneficiaryWithEmail(ownerId, "new@example.test")).thenReturn(false);
        when(executorAssignmentRepository.saveAndFlush(any(ExecutorAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ExecutorAssignmentResponse response = service.rescue(adminId, ownerId, "new@example.test");

        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup.getRevokedAt()).isNotNull();
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(primary.getExecutorUser()).isSameAs(candidate);
        assertThat(List.of(primary, backup)).filteredOn(a -> a.getStatus() == ExecutorStatus.ACTIVE).hasSize(1);
        assertThat(response.getEmail()).isEqualTo("new@example.test");
        verify(executorRoleManager).revokeIfUnused(backupUser, backup.getId());

        ArgumentCaptor<ExecutorAssignmentChangedEvent> event = ArgumentCaptor.forClass(ExecutorAssignmentChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().mails()).singleElement().satisfies(m -> {
            assertThat(m.type()).isEqualTo(ExecutorAssignmentChangedEvent.MailType.REPLACED_BY_ADMIN);
            assertThat(m.recipientEmail()).isEqualTo("backup@example.test");
        });
    }
}