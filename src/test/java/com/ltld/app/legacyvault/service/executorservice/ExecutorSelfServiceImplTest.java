package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.MyExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorSelfServiceImplTest {

    @Mock private ExecutorAssignmentRepository repository;
    @InjectMocks private ExecutorSelfServiceImpl service;

    private static ExecutorAssignment assignment(String ownerName, ExecutorType type, ExecutorStatus status) {
        return ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().id(UUID.randomUUID()).fullName(ownerName).build())
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
}
