package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorInvitationResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Lời mời làm Executor: Owner chỉ định (FR-06) tạo assignment PENDING; người được mời mở trang lời mời
 * (link trong mail / chuông) rồi đồng ý hoặc từ chối. Chỉ người đã ACCEPTED mới được tính là Executor dùng được.
 *
 * <p>Khóa theo đúng thứ tự của FR-06 (ExecutorManagementServiceImpl): DmsConfig -> User -> ExecutorAssignment,
 * để Owner thay / thu hồi cùng lúc không deadlock và không ghi đè nhau. READ_COMMITTED như FR-06: sau khi khóa User,
 * câu kiểm tra của revokeIfUnused phải thấy assignment mà Owner khác vừa commit.
 */
@Service
@RequiredArgsConstructor
public class ExecutorInvitationServiceImpl implements ExecutorInvitationService {

    static final int DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS = 14;
    static final String NOT_FOUND_MESSAGE = "Executor invitation not found.";
    static final String CLOSED_MESSAGE =
            "This invitation has already been answered or was withdrawn by the owner.";
    static final String EXPIRED_MESSAGE =
            "This invitation has expired. Ask the owner to send you a new one.";
    static final String HANDOVER_STARTED_MESSAGE =
            "The owner's Dead Man's Switch has started the handover process; this invitation can no longer be answered.";

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final UserRepository userRepository;
    private final ExecutorRoleManager executorRoleManager;
    private final ExecutorInvitationPolicy invitationPolicy;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public ExecutorInvitationResponse getInvitation(UUID assignmentId, String login) {
        User me = resolveUser(login);
        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> a.getExecutorUser().getId().equals(me.getId()))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException(NOT_FOUND_MESSAGE));
        DmsConfig dms = dmsConfigRepository.findByOwnerId(assignment.getOwner().getId()).orElse(null);
        return toResponse(assignment, dms);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExecutorInvitationResponse accept(UUID assignmentId, String login) {
        Locked locked = lockPendingInvitation(assignmentId, login);
        ExecutorAssignment assignment = locked.assignment();
        assignment.setResponseStatus(ExecutorResponseStatus.ACCEPTED);
        assignment.setRespondedAt(LocalDateTime.now());
        assignment.setDeclineReason(null);
        ExecutorAssignment saved = executorAssignmentRepository.saveAndFlush(assignment);

        audit(AuditAction.EXECUTOR_ACCEPTED, locked.me(), saved);
        publish(saved, locked.me(), true, null);
        return toResponse(saved, locked.dms());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExecutorInvitationResponse decline(UUID assignmentId, String login, String reason) {
        Locked locked = lockPendingInvitation(assignmentId, login);
        ExecutorAssignment assignment = locked.assignment();
        LocalDateTime now = LocalDateTime.now();
        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        // Từ chối = giải phóng vị trí: REVOKED để mọi chỗ đang xét "!= REVOKED" (giữ vị trí, xung đột vai trò...)
        // tự bỏ qua người này; Owner chỉ định người khác được ngay.
        assignment.setStatus(ExecutorStatus.REVOKED);
        assignment.setRevokedAt(now);
        assignment.setResponseStatus(ExecutorResponseStatus.DECLINED);
        assignment.setRespondedAt(now);
        assignment.setDeclineReason(trimmed);
        // User đã được khóa trong lockPendingInvitation; gỡ role EXECUTOR nếu không còn làm Executor cho Owner nào.
        executorRoleManager.revokeIfUnused(locked.me(), assignment.getId());
        ExecutorAssignment saved = executorAssignmentRepository.saveAndFlush(assignment);

        audit(AuditAction.EXECUTOR_DECLINED, locked.me(), saved);
        publish(saved, locked.me(), false, trimmed);
        return toResponse(saved, locked.dms());
    }

    private record Locked(User me, ExecutorAssignment assignment, DmsConfig dms) {
    }

    /**
     * Khóa DmsConfig -> User -> các assignment của Owner (như FR-06), rồi mới đọc assignment để thấy trạng thái mới nhất.
     * Không thuộc người gọi -> 404; đã trả lời / bị thu hồi / DMS đã kích hoạt -> 409 EXECUTOR_INVITATION_CLOSED.
     */
    private Locked lockPendingInvitation(UUID assignmentId, String login) {
        User caller = resolveUser(login);
        UUID ownerId = executorAssignmentRepository.findOwnerIdById(assignmentId)
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException(NOT_FOUND_MESSAGE));

        DmsConfig dms = dmsConfigRepository.findByOwnerIdForUpdate(ownerId).orElse(null);
        User me = userRepository.findByIdForUpdate(caller.getId())
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException(NOT_FOUND_MESSAGE));
        ExecutorAssignment assignment = executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId).stream()
                .filter(a -> a.getId().equals(assignmentId))
                .findFirst()
                // Owner vừa thay người trên cùng dòng: lời mời không còn là của người gọi
                .filter(a -> a.getExecutorUser().getId().equals(me.getId()))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException(NOT_FOUND_MESSAGE));

        if (dms != null && dms.getStatus() == DmsStatus.TRIGGERED) {
            throw new ExecutorManagementException(ErrorCode.EXECUTOR_INVITATION_CLOSED, HANDOVER_STARTED_MESSAGE);
        }
        if (assignment.getStatus() == ExecutorStatus.REVOKED
                || assignment.getResponseStatus() != ExecutorResponseStatus.PENDING) {
            throw new ExecutorManagementException(ErrorCode.EXECUTOR_INVITATION_CLOSED, CLOSED_MESSAGE);
        }
        // Quá hạn nhưng job quét chưa chạy tới: chặn ngay, đừng để trả lời muộn vẫn ăn
        if (invitationPolicy.isExpired(assignment, LocalDateTime.now())) {
            throw new ExecutorManagementException(ErrorCode.EXECUTOR_INVITATION_CLOSED, EXPIRED_MESSAGE);
        }
        return new Locked(me, assignment, dms);
    }

    // Principal là user id (JWT subject) hoặc email, giống ExecutorAccessGuard / DmsAutomationServiceImpl.
    private User resolveUser(String login) {
        if (login == null || login.isBlank()) {
            throw new ExecutorAssignmentNotFoundException(NOT_FOUND_MESSAGE);
        }
        String value = login.trim();
        Optional<User> user;
        try {
            user = userRepository.findById(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            user = userRepository.findByEmail(value.toLowerCase(Locale.ROOT));
        }
        return user.orElseThrow(() -> new ExecutorAssignmentNotFoundException(NOT_FOUND_MESSAGE));
    }

    // Actor là Executor; entity = ExecutorAssignment nên Nhật ký hoạt động của Owner tự hiện (FR-05).
    private void audit(AuditAction action, User me, ExecutorAssignment assignment) {
        auditLogService.log(action, AuditResult.SUCCESS, me.getId(), me.getEmail(), null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, assignment.getId().toString(),
                "ownerId=" + assignment.getOwner().getId() + ", executorType=" + assignment.getExecutorType()
                        + ", executorUserId=" + me.getId());
    }

    private void publish(ExecutorAssignment assignment, User me, boolean accepted, String reason) {
        User owner = assignment.getOwner();
        eventPublisher.publishEvent(new ExecutorInvitationRespondedEvent(assignment.getId(), owner.getId(),
                owner.getEmail(), owner.getFullName(), me.getFullName(), me.getEmail(),
                assignment.getExecutorType(), accepted, reason));
    }

    private ExecutorInvitationResponse toResponse(ExecutorAssignment a, DmsConfig dms) {
        boolean handoverStarted = dms != null && dms.getStatus() == DmsStatus.TRIGGERED;
        boolean expired = invitationPolicy.isExpired(a, LocalDateTime.now());
        Integer waitDays = dms == null ? null : dms.getBackupActivationWaitDays();
        return ExecutorInvitationResponse.builder()
                .assignmentId(a.getId())
                .ownerFullName(a.getOwner().getFullName())
                .ownerEmail(a.getOwner().getEmail())
                .executorType(a.getExecutorType())
                .status(a.getStatus())
                .responseStatus(a.getResponseStatus())
                .assignedAt(a.getAssignedAt() != null ? a.getAssignedAt() : a.getCreatedAt())
                .respondedAt(a.getRespondedAt())
                .invitationExpiresAt(a.getInvitationExpiresAt())
                .backupActivationWaitDays(waitDays != null ? waitDays : DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS)
                .canRespond(!handoverStarted && !expired && a.getStatus() != ExecutorStatus.REVOKED
                        && a.getResponseStatus() == ExecutorResponseStatus.PENDING)
                .build();
    }
}
