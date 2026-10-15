package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExecutorAccessGuard {

    static final String NOT_ACTIVE_MESSAGE = "Chưa đến thời điểm thực hiện quyền";

    private final ExecutorAssignmentRepository executorAssignmentRepository;

    /** 404 nếu không tồn tại hoặc không phải của người gọi (không lộ sự tồn tại); 403 nếu chưa ACTIVE. */
    public ExecutorAssignment requireActiveAssignment(UUID assignmentId, String executorLogin) {
        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> isSameUser(a.getExecutorUser(), executorLogin))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        if (assignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException(NOT_ACTIVE_MESSAGE);
        }
        return assignment;
    }

    private static boolean isSameUser(User user, String login) {
        if (user == null || login == null) {
            return false;
        }
        return login.equalsIgnoreCase(user.getEmail()) || login.equals(String.valueOf(user.getId()));
    }
}
