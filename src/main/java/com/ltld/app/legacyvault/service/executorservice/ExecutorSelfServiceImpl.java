package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.MyExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutorSelfServiceImpl implements ExecutorSelfService {

    private final ExecutorAssignmentRepository executorAssignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MyExecutorAssignmentResponse> listMyAssignments(String executorLogin) {
        if (executorLogin == null || executorLogin.isBlank()) {
            return List.of();
        }
        // Principal là user id (JWT subject) hoặc email, giống ExecutorAccessGuard.
        List<ExecutorAssignment> assignments = parseUuid(executorLogin)
                .map(executorAssignmentRepository::findLiveByExecutorUserId)
                .orElseGet(() -> executorAssignmentRepository
                        .findLiveByExecutorEmail(executorLogin.trim().toLowerCase(Locale.ROOT)));
        return assignments.stream().map(ExecutorSelfServiceImpl::toResponse).toList();
    }

    private static java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private static MyExecutorAssignmentResponse toResponse(ExecutorAssignment a) {
        return MyExecutorAssignmentResponse.builder()
                .assignmentId(a.getId())
                .ownerFullName(a.getOwner().getFullName())
                .executorType(a.getExecutorType())
                .status(a.getStatus())
                .assignedAt(a.getAssignedAt())
                .activatedAt(a.getActivatedAt())
                .acknowledgedAt(a.getAcknowledgedAt())
                .build();
    }
}
