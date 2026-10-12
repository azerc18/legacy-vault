package com.ltld.app.legacyvault.dev;

import com.ltld.app.legacyvault.dev.Fr07DevDtos.*;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CHỈ Ở PROFILE dev: dựng dữ liệu thay cho các FR chưa có (đăng ký, FR-03, FR-06),
 * cho phép "tua đồng hồ" cho lượt quét và đọc lại trạng thái để hiển thị trên console.
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class Fr07DevService {

    private static final String DEV_PASSWORD = "Passw0rd!";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final DmsAutomationService dmsAutomationService;
    private final DevExecutorNotificationService devNotificationService;
    private final EntityManager entityManager;

    @Transactional
    public SeedResponse seed(SeedRequest request) {
        int dueInDays = request != null && request.nextCheckinDueInDays() != null ? request.nextCheckinDueInDays() : -1;
        boolean withPrimary = request == null || request.withPrimary() == null || request.withPrimary();
        boolean withBackup = request == null || request.withBackup() == null || request.withBackup();
        int waitDays = request != null && request.backupWaitDays() != null ? request.backupWaitDays() : 14;

        String tag = UUID.randomUUID().toString().substring(0, 8);
        User owner = createUser("owner-" + tag + "@dev.test", "Dev Owner " + tag, "OWNER");

        DmsConfig config = dmsConfigRepository.save(DmsConfig.builder()
                .owner(owner)
                .backupActivationWaitDays(waitDays)
                .nextCheckinDueAt(LocalDateTime.now().plusDays(dueInDays))
                .build());

        ExecutorAssignment primary = null;
        if (withPrimary) {
            User user = createUser("primary-" + tag + "@dev.test", "Dev Primary " + tag, "EXECUTOR");
            primary = executorAssignmentRepository.save(newAssignment(owner, user, ExecutorType.PRIMARY));
        }
        ExecutorAssignment backup = null;
        if (withBackup) {
            User user = createUser("backup-" + tag + "@dev.test", "Dev Backup " + tag, "EXECUTOR");
            backup = executorAssignmentRepository.save(newAssignment(owner, user, ExecutorType.BACKUP));
        }

        return new SeedResponse(owner.getId().toString(), owner.getEmail(),
                primary == null ? null : primary.getId().toString(),
                primary == null ? null : primary.getExecutorUser().getEmail(),
                backup == null ? null : backup.getId().toString(),
                backup == null ? null : backup.getExecutorUser().getEmail(),
                config.getId().toString(), config.getNextCheckinDueAt());
    }

    /** Chạy đúng hai bước của scheduler nhưng với thời gian giả lập. */
    public void scan(LocalDateTime now) {
        dmsAutomationService.processOverdueDms(now);
        dmsAutomationService.activateBackupForUnacknowledgedPrimary(now);
    }

    public void setMailFailure(boolean enabled) {
        devNotificationService.setFailing(enabled);
    }

    @Transactional(readOnly = true)
    public StateResponse state(UUID ownerId) {
        DmsConfig config = dmsConfigRepository.findByOwnerId(ownerId).orElse(null);
        DmsView dms = config == null ? null : new DmsView(config.getId().toString(), config.getStatus().name(),
                config.getNextCheckinDueAt(), config.getTriggeredAt(), config.getBackupActivationWaitDays());

        List<ExecutorView> executors = new ArrayList<>();
        List<NotificationView> notifications = new ArrayList<>();
        List<UUID> entityIds = new ArrayList<>();
        if (config != null) {
            entityIds.add(config.getId());
        }
        for (ExecutorAssignment a : executorAssignmentRepository.findAllByOwnerId(ownerId)) {
            entityIds.add(a.getId());
            executors.add(new ExecutorView(a.getId().toString(), a.getExecutorType().name(), a.getStatus().name(),
                    a.getExecutorUser().getEmail(), a.getActivatedAt(), a.getRevokedAt(), a.getAcknowledgedAt()));
            for (Notification n : notificationRepository
                    .findAllByRecipientIdOrderByCreatedAtDesc(a.getExecutorUser().getId())) {
                if (config != null && config.getId().equals(n.getReferenceId())) {
                    notifications.add(new NotificationView(a.getExecutorUser().getEmail(), n.getType().name(),
                            n.getChannel().name(), n.getStatus().name(), n.getSentAt(), n.getContent()));
                }
            }
        }

        List<AuditView> audits = new ArrayList<>();
        if (!entityIds.isEmpty()) {
            List<AuditLog> logs = entityManager.createQuery(
                            "select a from AuditLog a where a.entityId in :ids order by a.createdAt asc", AuditLog.class)
                    .setParameter("ids", entityIds)
                    .getResultList();
            for (AuditLog l : logs) {
                audits.add(new AuditView(
                        l.getAction() == null ? null : l.getAction().name(),
                        l.getEntityType(),
                        l.getEntityId() == null ? null : l.getEntityId().toString(),
                        l.getActor() == null ? "SYSTEM" : l.getActor().getEmail(),
                        l.getMetadata() == null ? null : l.getMetadata().toString(),
                        l.getCreatedAt() == null ? null : LocalDateTime.ofInstant(l.getCreatedAt(), java.time.ZoneId.systemDefault())
                ));
            }
        }
        return new StateResponse(dms, executors, notifications, audits, devNotificationService.isFailing());
    }

    private User createUser(String email, String fullName, String roleCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleCode));
        return userRepository.save(User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(DEV_PASSWORD))
                .status(UserStatus.ACTIVE)
                .roles(Set.of(role))
                .build());
    }

    private ExecutorAssignment newAssignment(User owner, User executor, ExecutorType type) {
        return ExecutorAssignment.builder()
                .owner(owner)
                .executorUser(executor)
                .executorType(type)
                .status(ExecutorStatus.INACTIVE)
                .build();
    }
}
