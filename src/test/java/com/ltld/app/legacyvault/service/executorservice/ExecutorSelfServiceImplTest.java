package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.MyExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.VerificationExchangeMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorSelfServiceImplTest {

    @Mock private ExecutorAssignmentRepository repository;
    @Mock private VaultRepository vaultRepository;
    @Mock private LegalVerificationRequestRepository legalVerificationRequestRepository;
    @Mock private VerificationExchangeMessageRepository verificationExchangeMessageRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @InjectMocks private ExecutorSelfServiceImpl service;

    private static final User EXECUTOR = User.builder().id(UUID.randomUUID()).email("exec@example.com").build();

    private static ExecutorAssignment assignment(String ownerName, ExecutorType type, ExecutorStatus status) {
        return ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().id(UUID.randomUUID()).fullName(ownerName).email("owner@example.com").build())
                .executorUser(EXECUTOR)
                .executorType(type)
                .status(status)
                .assignedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    @Test
    void listMyAssignments_userIdLogin_returnsAssignmentsOfMultipleOwners() {
        UUID userId = UUID.randomUUID();
        ExecutorAssignment a1 = assignment("Owner A", ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);
        ExecutorAssignment a2 = assignment("Owner B", ExecutorType.BACKUP, ExecutorStatus.INACTIVE);
        when(repository.findLiveByExecutorUserId(userId)).thenReturn(List.of(a1, a2));

        List<MyExecutorAssignmentResponse> result = service.listMyAssignments(userId.toString());

        assertEquals(2, result.size());
        assertEquals(a1.getId(), result.get(0).getAssignmentId());
        assertEquals("Owner A", result.get(0).getOwnerFullName());
        assertEquals(ExecutorStatus.INACTIVE, result.get(1).getStatus());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), result.get(0).getAssignedAt());
    }

    @Test
    void listMyAssignments_emailLogin_isLowercasedAndQueriedByEmail() {
        when(repository.findLiveByExecutorEmail("exec@example.com"))
                .thenReturn(List.of(assignment("Owner A", ExecutorType.PRIMARY, ExecutorStatus.ACTIVE)));

        List<MyExecutorAssignmentResponse> result = service.listMyAssignments("  Exec@Example.com ");

        assertEquals(1, result.size());
        verify(repository).findLiveByExecutorEmail("exec@example.com");
    }

    @Test
    void listMyAssignments_noAssignments_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(repository.findLiveByExecutorUserId(userId)).thenReturn(List.of());

        assertTrue(service.listMyAssignments(userId.toString()).isEmpty());
    }

    @Test
    void listMyAssignments_blankOrNullLogin_returnsEmptyWithoutQuery() {
        assertTrue(service.listMyAssignments(null).isEmpty());
        assertTrue(service.listMyAssignments("  ").isEmpty());
        verifyNoInteractions(repository);
    }

    // ---------- B8: số liệu dashboard ----------

    @Test
    void listMyAssignments_b8_fillsDashboardFieldsPerOwner() {
        UUID userId = EXECUTOR.getId();
        ExecutorAssignment active = assignment("Owner A", ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);
        active.setActivatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        ExecutorAssignment inactive = assignment(null, ExecutorType.BACKUP, ExecutorStatus.INACTIVE);
        UUID ownerA = active.getOwner().getId();
        UUID ownerB = inactive.getOwner().getId();
        when(repository.findLiveByExecutorUserId(userId)).thenReturn(List.of(active, inactive));
        when(vaultRepository.countByOwnerIds(List.of(ownerA, ownerB)))
                .thenReturn(List.<Object[]>of(new Object[]{ownerA, 3L}));
        when(dmsConfigRepository.findAllByOwnerIdIn(List.of(ownerA, ownerB))).thenReturn(List.of(
                DmsConfig.builder().owner(active.getOwner()).backupActivationWaitDays(14).build(),
                DmsConfig.builder().owner(inactive.getOwner()).backupActivationWaitDays(14).build()));
        LegalVerificationRequest newest = LegalVerificationRequest.builder().id(UUID.randomUUID())
                .owner(active.getOwner()).status(LegalVerificationStatus.PENDING).build();
        LegalVerificationRequest older = LegalVerificationRequest.builder().id(UUID.randomUUID())
                .owner(active.getOwner()).status(LegalVerificationStatus.REJECTED).build();
        when(legalVerificationRequestRepository.findByExecutorIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(newest, older));
        when(verificationExchangeMessageRepository.countUnreadForExecutor(any())).thenReturn(List.<Object[]>of(
                new Object[]{newest.getId(), 1L}, new Object[]{older.getId(), 2L}));

        List<MyExecutorAssignmentResponse> result = service.listMyAssignments(userId.toString());

        MyExecutorAssignmentResponse a = result.get(0);
        assertEquals("owner@example.com", a.getOwnerEmail());
        assertEquals(3, a.getVaultCount());
        assertEquals(LegalVerificationStatus.PENDING, a.getLatestRequestStatus());
        assertEquals(3, a.getUnreadMessageCount());
        assertEquals(LocalDateTime.of(2026, 10, 15, 8, 0), a.getAcknowledgeDeadlineAt());

        MyExecutorAssignmentResponse b = result.get(1);
        assertNull(b.getOwnerFullName());
        assertEquals("owner@example.com", b.getOwnerEmail());
        assertEquals(0, b.getVaultCount());
        assertNull(b.getLatestRequestStatus());
        assertEquals(0, b.getUnreadMessageCount());
        assertNull(b.getAcknowledgeDeadlineAt(), "INACTIVE -> no deadline");
    }

    @Test
    void listMyAssignments_b8_acknowledged_hasNoDeadline_andNoRequestsSkipsUnreadQuery() {
        UUID userId = EXECUTOR.getId();
        ExecutorAssignment a = assignment("Owner A", ExecutorType.PRIMARY, ExecutorStatus.ACTIVE);
        a.setActivatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        a.setAcknowledgedAt(LocalDateTime.of(2026, 10, 2, 8, 0));
        when(repository.findLiveByExecutorUserId(userId)).thenReturn(List.of(a));
        when(dmsConfigRepository.findAllByOwnerIdIn(any())).thenReturn(List.of(
                DmsConfig.builder().owner(a.getOwner()).backupActivationWaitDays(14).build()));

        MyExecutorAssignmentResponse result = service.listMyAssignments(userId.toString()).get(0);

        assertNull(result.getAcknowledgeDeadlineAt());
        verifyNoInteractions(verificationExchangeMessageRepository);
    }
}
