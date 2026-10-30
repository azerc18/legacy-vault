package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.*;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.service.executorservice.ExecutorRoleManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminExecutorRescueServiceImpl implements AdminExecutorRescueService {

    private final DmsConfigRepository dmsConfigRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final UserRepository userRepository;
    private final VaultRepository vaultRepository;
    private final ExecutorRoleManager executorRoleManager;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final LegalVerificationRequestRepository legalVerificationRequestRepository;

    // READ_COMMITTED + thứ tự khóa DmsConfig -> ExecutorAssignment -> User, giống FR-06/FR-07, để không deadlock
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExecutorAssignmentResponse rescue(UUID adminId, UUID ownerId, String rawEmail) {
        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsException("This owner has not configured a Dead Man's Switch.", HttpStatus.NOT_FOUND));
        List<ExecutorAssignment> assignments = executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId);
        LocalDateTime now = LocalDateTime.now();
        if (!ExecutorRescueRule.rescueNeeded(config, assignments, now,
                a -> legalVerificationRequestRepository.existsByOwnerIdAndExecutorIdAndCreatedAtAfter(
                        ownerId, a.getExecutorUser().getId(), a.getActivatedAt()))) {
            throw new DmsException(config.getStatus() != DmsStatus.TRIGGERED
                    ? "Rescue is only available after the Dead Man's Switch has been triggered."
                    : "This owner still has an active executor who can handle the handover.", HttpStatus.CONFLICT);
        }

        User owner = config.getOwner();
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        User candidate = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new DmsException(
                        "This email does not belong to a verified, active LegacyVault account.", HttpStatus.UNPROCESSABLE_ENTITY));
        if (candidate.getId().equals(owner.getId())) {
            throw new DmsException("The owner cannot be their own executor.", HttpStatus.BAD_REQUEST);
        }
        // Q26: người thụ hưởng không được đồng thời là Executor
        if (vaultRepository.existsBeneficiaryWithEmail(ownerId, email)) {
            throw new DmsException("This person is a beneficiary of the owner and cannot also be the executor.", HttpStatus.CONFLICT);
        }
        boolean isBackup = assignments.stream().anyMatch(a -> a.getExecutorType() == ExecutorType.BACKUP
                && a.getStatus() != ExecutorStatus.REVOKED
                && a.getExecutorUser().getId().equals(candidate.getId()));
        if (isBackup) {
            throw new DmsException("This person is already the backup executor of the owner. Choose someone else.", HttpStatus.CONFLICT);
        }

        ExecutorAssignment primary = assignments.stream()
                .filter(a -> a.getExecutorType() == ExecutorType.PRIMARY).findFirst().orElse(null);
        User previous = primary != null && primary.getStatus() != ExecutorStatus.REVOKED
                && !primary.getExecutorUser().getId().equals(candidate.getId())
                ? primary.getExecutorUser() : null;

        // Khóa user theo thứ tự id TRƯỚC khi đọc roles (LAZY), giống ExecutorManagementServiceImpl
        lockUsersInOrder(candidate.getId(), previous != null ? previous.getId() : null);

        ExecutorAssignment assignment = primary != null
                ? primary
                : ExecutorAssignment.builder().owner(owner).executorType(ExecutorType.PRIMARY).build();
        assignment.setExecutorUser(candidate);
        assignment.setStatus(ExecutorStatus.ACTIVE);   // DMS đã TRIGGERED -> kích hoạt luôn
        assignment.setAssignedAt(now);
        assignment.setActivatedAt(now);
        assignment.setAcknowledgedAt(null);
        assignment.setRevokedAt(null);

        executorRoleManager.grant(candidate);
        if (previous != null) {
            executorRoleManager.revokeIfUnused(previous, assignment.getId());
        }
        ExecutorAssignment saved = executorAssignmentRepository.saveAndFlush(assignment);

        // Mail kích hoạt: lưu Notification FAILED, attemptCount = 0.
        // Job escalateUnresponsivePrimaries (mỗi 5 phút) sẽ gửi qua retryFailedNotification và đặt sentAt,
        // nên đồng hồ 14 ngày BR-03 và cơ chế gửi lại dùng đúng logic hiện có, không gửi mail trong transaction.
        notificationRepository.save(Notification.builder()
                .recipient(candidate)
                .type(NotificationType.EXECUTOR_ACTIVATED)
                .channel(NotificationChannel.EMAIL)
                .referenceType(DmsActivationProcessor.ENTITY_DMS_CONFIG)
                .referenceId(config.getId())
                .content(DmsActivationProcessor.activationMessage(owner, saved))
                .status(NotificationStatus.FAILED)
                .attemptCount(0)
                .build());

        if (previous != null) {
            // Gửi sau commit qua listener có sẵn
            eventPublisher.publishEvent(new ExecutorAssignmentChangedEvent(List.of(
                    ExecutorMail.revoked(previous.getEmail(), owner.getFullName(), owner.getEmail(), ExecutorType.PRIMARY))));
        }

        String adminEmail = userRepository.findById(adminId).map(User::getEmail).orElse(null);
        auditLogService.log(AuditAction.EXECUTOR_ASSIGNED_BY_ADMIN, AuditResult.SUCCESS, adminId, adminEmail, null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, saved.getId().toString(),
                "ownerId=" + ownerId + ", executorUserId=" + candidate.getId()
                        + (previous != null ? ", previousExecutorUserId=" + previous.getId() : ""));

        return ExecutorAssignmentResponse.builder()
                .assignmentId(saved.getId())
                .executorType(saved.getExecutorType())
                .status(saved.getStatus())
                .email(candidate.getEmail())
                .fullName(candidate.getFullName())
                .assignedAt(saved.getAssignedAt())
                .activatedAt(saved.getActivatedAt())
                .revokedAt(saved.getRevokedAt())
                .build();
    }

    private void lockUsersInOrder(UUID... ids) {
        Arrays.stream(ids).filter(Objects::nonNull).distinct().sorted()
                .forEach(userRepository::findByIdForUpdate);
    }
}