package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DmsAutomationServiceImpl implements DmsAutomationService {

    private final DmsConfigRepository dmsConfigRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final DmsActivationProcessor processor;

    // Không @Transactional ở đây: mỗi Owner chạy trong transaction riêng của processor,
    // một Owner lỗi chỉ bị log, không chặn các Owner còn lại.
    @Override
    public void processOverdueDms(LocalDateTime now) {
        for (DmsConfig config : dmsConfigRepository
                .findAllByStatusAndNextCheckinDueAtBefore(DmsStatus.ACTIVE, now)) {
            UUID configId = config.getId();
            runIsolated("trigger", configId, () -> processor.trigger(configId, now));
        }
    }

    @Override
    public void escalateUnresponsivePrimaries(LocalDateTime now) {
        for (DmsConfig config : dmsConfigRepository.findAllByStatus(DmsStatus.TRIGGERED)) {
            UUID configId = config.getId();
            runIsolated("escalate", configId, () -> processor.escalateIfPrimaryUnresponsive(configId, now));
        }
    }

    @Override
    @Transactional
    public ExecutorActivationResponse acknowledgeActivation(UUID assignmentId, String executorLogin) {
        // Không phải của người đang đăng nhập => 404 như "không tồn tại" (không lộ id của người khác).
        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> isSameUser(a.getExecutorUser(), executorLogin))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        if (assignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }

        DmsConfig config = dmsConfigRepository.findByOwnerId(assignment.getOwner().getId())
                .filter(c -> c.getStatus() == DmsStatus.TRIGGERED)
                .orElseThrow(() -> new DmsNotTriggeredException("Dead Man's Switch has not been triggered."));

        // Idempotent: xác nhận lần 2 không đổi gì và không ghi audit thêm.
        if (assignment.getAcknowledgedAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            assignment.setAcknowledgedAt(now);
            executorAssignmentRepository.save(assignment);

            notificationRepository
                    .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                            assignment.getExecutorUser().getId(),
                            NotificationType.EXECUTOR_ACTIVATED,
                            DmsActivationProcessor.ENTITY_DMS_CONFIG, config.getId())
                    .ifPresent(n -> {
                        n.setStatus(NotificationStatus.READ);
                        notificationRepository.save(n);
                    });

            // vaultId = null: DMS gắn với Owner, không với một Vault cụ thể.
            User executor = assignment.getExecutorUser();
            auditLogService.log(AuditAction.EXECUTOR_ACKNOWLEDGED, AuditResult.SUCCESS,
                    executor.getId(), executor.getEmail(), null,
                    DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, assignment.getId().toString(),
                    DmsActivationProcessor.ownerDetail(assignment.getOwner().getId())
                            + ", executorType=" + assignment.getExecutorType());
        }

        return ExecutorActivationResponse.builder()
                .id(assignment.getId())
                .executorType(assignment.getExecutorType())
                .status(assignment.getStatus())
                .activatedAt(assignment.getActivatedAt())
                .acknowledgedAt(assignment.getAcknowledgedAt())
                .build();
    }

    // Phiên đăng nhập (JWT) có thể mang email hoặc user id làm subject; chấp nhận cả hai.
    private boolean isSameUser(User user, String login) {
        if (user == null || login == null) {
            return false;
        }
        return login.equalsIgnoreCase(user.getEmail()) || login.equals(String.valueOf(user.getId()));
    }

    private void runIsolated(String step, UUID configId, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("FR-07 step '{}' failed for dmsConfig {}: {}", step, configId, e.getMessage(), e);
        }
    }
}
