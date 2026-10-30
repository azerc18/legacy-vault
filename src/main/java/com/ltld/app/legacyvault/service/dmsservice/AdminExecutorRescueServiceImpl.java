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
import java.util.stream.Collectors;

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
        if (!ExecutorRescueRule.rescueNeeded(config, assignments, now, a -> hasSubmitted(ownerId, a))) {
            throw new DmsException(refusedMessage(config, assignments), HttpStatus.CONFLICT);
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

        // FR-07: Executor ACTIVE khác dòng PRIMARY (Backup đã được kích hoạt rồi bỏ dở) cũng bị thu hồi,
        // để mỗi Owner chỉ có một Executor ACTIVE.
        List<ExecutorAssignment> displaced = assignments.stream()
                .filter(a -> a != primary && a.getStatus() == ExecutorStatus.ACTIVE)
                .toList();

        // Khóa MỌI user liên quan theo thứ tự id tăng dần TRƯỚC khi đọc roles (LAZY), giống ExecutorManagementServiceImpl
        List<UUID> toLock = new ArrayList<>();
        toLock.add(candidate.getId());
        if (previous != null) toLock.add(previous.getId());
        displaced.forEach(a -> toLock.add(a.getExecutorUser().getId()));
        lockUsersInOrder(toLock.toArray(new UUID[0]));

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
        for (ExecutorAssignment d : displaced) {
            d.setStatus(ExecutorStatus.REVOKED);
            d.setRevokedAt(now);
            executorRoleManager.revokeIfUnused(d.getExecutorUser(), d.getId());
        }
        ExecutorAssignment saved = executorAssignmentRepository.saveAndFlush(assignment);

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

        // Mail "Admin đã thay bạn" cho mọi người bị thay, gửi sau commit qua listener có sẵn
        int waitDays = config.getBackupActivationWaitDays();
        List<ExecutorMail> mails = new ArrayList<>();
        if (previous != null) {
            mails.add(ExecutorMail.replacedByAdmin(previous.getEmail(), owner.getFullName(), owner.getEmail(),
                    ExecutorType.PRIMARY, waitDays));
        }
        for (ExecutorAssignment d : displaced) {
            mails.add(ExecutorMail.replacedByAdmin(d.getExecutorUser().getEmail(), owner.getFullName(),
                    owner.getEmail(), d.getExecutorType(), waitDays));
        }
        if (!mails.isEmpty()) {
            eventPublisher.publishEvent(new ExecutorAssignmentChangedEvent(mails));
        }

        String revokedIds = displaced.stream().map(d -> d.getExecutorUser().getId().toString())
                .collect(Collectors.joining(","));
        String adminEmail = userRepository.findById(adminId).map(User::getEmail).orElse(null);
        auditLogService.log(AuditAction.EXECUTOR_ASSIGNED_BY_ADMIN, AuditResult.SUCCESS, adminId, adminEmail, null,
                DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT, saved.getId().toString(),
                "ownerId=" + ownerId + ", executorUserId=" + candidate.getId()
                        + (previous != null ? ", previousExecutorUserId=" + previous.getId() : "")
                        + (displaced.isEmpty() ? "" : ", revokedExecutorUserIds=" + revokedIds));

        return ExecutorAssignmentResponse.builder()
                .assignmentId(saved.getId())
                .executorType(saved.getExecutorType())
                .status(saved.getStatus())
                .email(candidate.getEmail())
                .fullName(candidate.getFullName())
                .assignedAt(saved.getAssignedAt())
                .activatedAt(saved.getActivatedAt())
                .acknowledgedAt(saved.getAcknowledgedAt())
                .revokedAt(saved.getRevokedAt())
                .build();
    }

    private boolean hasSubmitted(UUID ownerId, ExecutorAssignment a) {
        return legalVerificationRequestRepository.existsByOwnerIdAndExecutorIdAndCreatedAtAfter(
                ownerId, a.getExecutorUser().getId(), a.getActivatedAt());
    }

    private static String refusedMessage(DmsConfig config, Collection<ExecutorAssignment> assignments) {
        if (config.getStatus() != DmsStatus.TRIGGERED) {
            return "Rescue is only available after the Dead Man's Switch has been triggered.";
        }
        if (ExecutorRescueRule.hasStandbyBackup(assignments)) {
            return "This owner has a standby backup executor who can take over the handover.";
        }
        return "This owner still has an active executor who can handle the handover.";
    }

    private void lockUsersInOrder(UUID... ids) {
        Arrays.stream(ids).filter(Objects::nonNull).distinct().sorted()
                .forEach(userRepository::findByIdForUpdate);
    }
}
