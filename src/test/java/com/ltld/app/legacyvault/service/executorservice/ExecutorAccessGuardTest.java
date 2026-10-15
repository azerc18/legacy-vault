package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorAccessGuardTest {

    private static final String EMAIL = "executor@example.com";

    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @InjectMocks private ExecutorAccessGuard guard;

    private UUID assignmentId;
    private UUID executorId;
    private User executor;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        executorId = UUID.randomUUID();
        executor = User.builder().id(executorId).email(EMAIL).build();
    }

    private ExecutorAssignment assignment(ExecutorStatus status) {
        return ExecutorAssignment.builder().id(assignmentId).executorUser(executor).status(status).build();
    }

    @Test
    @DisplayName("Active assignment of the caller is returned")
    void activeAssignmentOfCaller_isReturned() {
        ExecutorAssignment a = assignment(ExecutorStatus.ACTIVE);
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(a));

        assertSame(a, guard.requireActiveAssignment(assignmentId, EMAIL));
    }

    @Test
    @DisplayName("Upper-case email and user id string both identify the caller")
    void emailIsCaseInsensitive_andUserIdIsAccepted() {
        ExecutorAssignment a = assignment(ExecutorStatus.ACTIVE);
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(a));

        assertSame(a, guard.requireActiveAssignment(assignmentId, "EXECUTOR@EXAMPLE.COM"));
        assertSame(a, guard.requireActiveAssignment(assignmentId, executorId.toString()));
    }

    @Test
    @DisplayName("Unknown assignment -> 404")
    void unknownAssignment_throwsNotFound() {
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> guard.requireActiveAssignment(assignmentId, EMAIL));
    }

    @Test
    @DisplayName("Assignment of another executor -> 404, never 403")
    void assignmentOfAnotherExecutor_throwsNotFound() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> guard.requireActiveAssignment(assignmentId, "attacker@example.com"));
    }

    @Test
    @DisplayName("Another executor's INACTIVE assignment -> 404, so its status is not disclosed")
    void inactiveAssignmentOfAnotherExecutor_throwsNotFound() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.INACTIVE)));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> guard.requireActiveAssignment(assignmentId, "attacker@example.com"));
    }

    @Test
    @DisplayName("Null login -> 404")
    void nullLogin_throwsNotFound() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.ACTIVE)));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> guard.requireActiveAssignment(assignmentId, null));
    }

    @Test
    @DisplayName("Assignment without an executor user -> 404")
    void assignmentWithoutExecutorUser_throwsNotFound() {
        ExecutorAssignment a = ExecutorAssignment.builder().id(assignmentId).status(ExecutorStatus.ACTIVE).build();
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(a));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> guard.requireActiveAssignment(assignmentId, EMAIL));
    }

    @Test
    @DisplayName("Caller's INACTIVE assignment -> 403 with the SRS message")
    void inactiveAssignmentOfCaller_throwsNotActive() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.INACTIVE)));

        ExecutorNotActiveException ex = assertThrows(ExecutorNotActiveException.class,
                () -> guard.requireActiveAssignment(assignmentId, EMAIL));
        assertEquals("Chưa đến thời điểm thực hiện quyền", ex.getMessage());
    }

    @Test
    @DisplayName("Caller's REVOKED assignment -> 403")
    void revokedAssignmentOfCaller_throwsNotActive() {
        when(executorAssignmentRepository.findById(assignmentId))
                .thenReturn(Optional.of(assignment(ExecutorStatus.REVOKED)));

        assertThrows(ExecutorNotActiveException.class,
                () -> guard.requireActiveAssignment(assignmentId, EMAIL));
    }
}
