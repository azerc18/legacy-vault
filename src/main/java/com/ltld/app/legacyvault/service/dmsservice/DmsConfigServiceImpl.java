package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class DmsConfigServiceImpl implements DmsConfigService {

    // Giữ backupActivationWaitDays hiện có; cấu hình mới dùng BR-03 (14 ngày).
    static final int DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS = 14;
    static final String SUSPENDED_MESSAGE = "The Dead Man's Switch has been suspended by an administrator. Please contact an admin.";
    static final String HANDOVER_STARTED_MESSAGE = "The Dead Man's Switch has started the handover process and cannot be changed.";
    static final String INCOMPLETE_CONFIG_MESSAGE = "The Dead Man's Switch configuration is incomplete. Please save the configuration again.";
    static final String SUSPEND_TRIGGERED_MESSAGE = "The Dead Man's Switch has started the handover process and cannot be suspended.";
    static final String ALREADY_SUSPENDED_MESSAGE = "The Dead Man's Switch is already suspended.";
    static final String NOT_SUSPENDED_MESSAGE = "The Dead Man's Switch is not suspended.";
    static final String NO_PRIMARY_EXECUTOR_MESSAGE = "Assign a primary executor before enabling the Dead Man's Switch.";
    static final String NO_ACTIVATION_TARGET_MESSAGE = "No executor is available to receive the handover. "
            + "Assign an executor before saving the Dead Man's Switch configuration.";
    static final String RESUME_NO_ACTIVATION_TARGET_MESSAGE =
            "The owner has no executor available to receive the handover; the Dead Man's Switch cannot be resumed.";

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    @Override
    @Transactional(readOnly = true)
    public DmsConfigResponse getConfig(UUID ownerId) {
        return toResponse(findConfig(ownerId));
    }

    // FR-03: READ_COMMITTED như FR-06. Với REPEATABLE READ, findByOwnerIdForUpdate trên dòng chưa có sẽ khóa gap
    // của uk_dms_config_owner; hai lần tạo đồng thời giữ gap của nhau rồi chờ nhau ở INSERT / khóa Executor (deadlock).
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpsertResult upsertConfig(UUID ownerId, DmsConfigRequest request) {
        // Khóa DmsConfig (nếu đã có) TRƯỚC ExecutorAssignment, cùng thứ tự với FR-06/FR-07.
        DmsConfig existing = dmsConfigRepository.findByOwnerIdForUpdate(ownerId).orElse(null);
        // SRS: DMS đã kích hoạt thì quy trình bàn giao không thể bị Owner hủy hay cấu hình lại.
        if (existing != null && existing.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException("The Dead Man's Switch has been triggered; its configuration cannot be changed.",
                    HttpStatus.CONFLICT);
        }
        // Q28: SUSPENDED do Admin đặt và gỡ; Owner không tự bật lại bằng cách lưu cấu hình.
        if (existing != null && existing.getStatus() == DmsStatus.SUSPENDED) {
            throw new DmsException(SUSPENDED_MESSAGE, HttpStatus.CONFLICT);
        }
        // FR-03 precondition: có ít nhất 1 Vault.
        if (!vaultRepository.existsByOwnerId(ownerId)) {
            throw new DmsException("Create at least one vault before configuring the Dead Man's Switch.",
                    HttpStatus.CONFLICT);
        }
        // FR-03: bật lần đầu phải có Executor chính chưa REVOKED; lưu lại cấu hình chỉ cần trigger còn chọn được
        // người (cùng quy tắc với FR-07), để Owner đã thu hồi Primary nhưng còn Backup vẫn lưu được.
        List<ExecutorAssignment> executors = existing == null
                ? lockExecutors(ownerId, list -> list.stream().anyMatch(ExecutorActivationRule::isUsablePrimary),
                        NO_PRIMARY_EXECUTOR_MESSAGE)
                : lockExecutors(ownerId, list -> ExecutorActivationRule.pickActivationTarget(list).isPresent(),
                        NO_ACTIVATION_TARGET_MESSAGE);

        User owner = existing != null ? existing.getOwner() : userRepository.findById(ownerId)
                .orElseThrow(() -> new DmsException("Owner not found.", HttpStatus.NOT_FOUND));
        DmsConfig config = existing != null ? existing : DmsConfig.builder()
                .owner(owner)
                .backupActivationWaitDays(DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS)
                .build();
        if (config.getBackupActivationWaitDays() == null) {
            config.setBackupActivationWaitDays(DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS);
        }

        LocalDateTime now = LocalDateTime.now();
        config.setStatus(DmsStatus.ACTIVE);
        config.setCheckinIntervalDays(request.getCheckinIntervalDays());
        config.setGracePeriodDays(request.getGracePeriodDays());
        config.setReminderCount(request.getReminderCount());
        config.setNotificationChannel(NotificationChannel.EMAIL);
        startNewCycle(config, now);

        boolean created = existing == null;
        try {
            config = dmsConfigRepository.saveAndFlush(config);
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu tạo đồng thời: yêu cầu thua vi phạm uk_dms_config_owner.
            if (created) {
                throw new DmsException("The Dead Man's Switch configuration was just created by another request. Please reload.",
                        HttpStatus.CONFLICT);
            }
            throw e;
        }

        // Ghi audit SAU flush: audit vẫn được lưu khi rollback nên không được ghi cho thay đổi chưa chắc thành công.
        audit(AuditAction.DMS_CONFIGURED, owner, config, "created=" + created
                + ", checkinIntervalDays=" + config.getCheckinIntervalDays()
                + ", gracePeriodDays=" + config.getGracePeriodDays()
                + ", reminderCount=" + config.getReminderCount());
        return new UpsertResult(toResponse(config, executors), created);
    }

    @Override
    @Transactional
    public DmsConfigResponse checkIn(UUID ownerId) {
        DmsConfig config = findConfig(ownerId);
        if (config.getStatus() != DmsStatus.ACTIVE) {
            // TRIGGERED: SRS không cho Owner hủy quy trình bàn giao đã kích hoạt bằng check-in.
            // SUSPENDED (Q28): chỉ Admin gỡ được.
            throw new DmsException(switch (config.getStatus()) {
                case TRIGGERED -> "The Dead Man's Switch has been triggered; check-in is no longer possible.";
                case SUSPENDED -> SUSPENDED_MESSAGE;
                default -> "The Dead Man's Switch is not active.";
            }, HttpStatus.CONFLICT);
        }
        assertScheduleComplete(config);

        LocalDateTime now = LocalDateTime.now();
        startNewCycle(config, now);
        config = dmsConfigRepository.saveAndFlush(config);
        dmsCheckinRepository.saveAndFlush(DmsCheckin.builder()
                .dmsConfig(config)
                .checkinType(DmsCheckinType.OWNER_RESPONDED)
                .occurredAt(now)
                .build());

        audit(AuditAction.DMS_CHECKIN, config.getOwner(), config,
                "nextCheckinDueAt=" + config.getNextCheckinDueAt());
        return toResponse(config);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertHandoverNotStarted(UUID ownerId) {
        // Khóa như ensureDmsNotTriggered (FR-06): trigger FR-07 ghi trước thì chờ commit rồi thấy TRIGGERED;
        // khóa trước thì trigger lỗi @Version và rollback. Chưa có DmsConfig thì không có gì để chặn.
        dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .filter(c -> c.getStatus() == DmsStatus.TRIGGERED)
                .ifPresent(c -> {
                    throw new DmsException(HANDOVER_STARTED_MESSAGE, HttpStatus.CONFLICT);
                });
    }

    @Override
    @Transactional
    public DmsConfigResponse suspendByAdmin(UUID ownerId, UUID adminId, String reason) {
        DmsConfig config = findConfigForUpdate(ownerId);
        if (config.getStatus() == DmsStatus.TRIGGERED) {
            // Không tạm dừng quy trình bàn giao đã kích hoạt.
            throw new DmsException(SUSPEND_TRIGGERED_MESSAGE, HttpStatus.CONFLICT);
        }
        if (config.getStatus() == DmsStatus.SUSPENDED) {
            throw new DmsException(ALREADY_SUSPENDED_MESSAGE, HttpStatus.CONFLICT);
        }

        // Giữ nguyên các mốc lịch: job chỉ quét ACTIVE, resume sẽ tính lại chu kỳ mới.
        config.setStatus(DmsStatus.SUSPENDED);
        config = dmsConfigRepository.saveAndFlush(config);

        String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
        auditByAdmin(AuditAction.DMS_SUSPENDED, adminId, config,
                trimmedReason == null ? null : "reason=" + trimmedReason);
        return toResponse(config);
    }

    // READ_COMMITTED: cùng lý do với upsertConfig.
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DmsConfigResponse resumeByAdmin(UUID ownerId, UUID adminId) {
        DmsConfig config = findConfigForUpdate(ownerId);
        if (config.getStatus() != DmsStatus.SUSPENDED) {
            throw new DmsException(NOT_SUSPENDED_MESSAGE, HttpStatus.CONFLICT);
        }
        assertScheduleComplete(config);
        // FR-03: mở lại = bật lại DMS đã có cấu hình, cùng quy tắc với lưu lại cấu hình: trigger phải còn chọn được
        // người. Owner có thể đã thu hồi Executor trong lúc DMS bị tạm dừng (FR-06 cho phép), nên phải kiểm lại ở đây.
        List<ExecutorAssignment> executors = lockExecutors(ownerId,
                list -> ExecutorActivationRule.pickActivationTarget(list).isPresent(),
                RESUME_NO_ACTIVATION_TARGET_MESSAGE);

        // Chu kỳ mới từ lúc mở lại: hạn cũ có thể đã qua trong thời gian tạm dừng, không được trigger ngay.
        config.setStatus(DmsStatus.ACTIVE);
        startNewCycle(config, LocalDateTime.now());
        config = dmsConfigRepository.saveAndFlush(config);

        auditByAdmin(AuditAction.DMS_RESUMED, adminId, config, "nextCheckinDueAt=" + config.getNextCheckinDueAt());
        return toResponse(config, executors);
    }

    private DmsConfig findConfig(UUID ownerId) {
        return dmsConfigRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new DmsException("The Dead Man's Switch has not been configured.", HttpStatus.NOT_FOUND));
    }

    // Khóa PESSIMISTIC_FORCE_INCREMENT: trigger FR-07 / nhắc check-in đang ghi cùng lúc sẽ lỗi @Version và rollback.
    private DmsConfig findConfigForUpdate(UUID ownerId) {
        return dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsException("The Dead Man's Switch has not been configured.", HttpStatus.NOT_FOUND));
    }

    /**
     * FR-03: khóa FOR UPDATE các dòng Executor của Owner rồi mới kiểm {@code usable}; không đạt -> 409 {@code message}.
     * FR-06 revokeExecutor khóa cùng các dòng này rồi mới đọc trạng thái DMS, nên "bật DMS lần đầu" (chưa có dòng
     * DmsConfig để khóa chung) và "thu hồi Executor cuối cùng" chạy lần lượt, không cùng commit được.
     * Gọi SAU khi đã khóa DmsConfig (thứ tự DmsConfig -> User -> ExecutorAssignment).
     */
    private List<ExecutorAssignment> lockExecutors(UUID ownerId, Predicate<List<ExecutorAssignment>> usable,
                                                   String message) {
        List<ExecutorAssignment> executors = executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId);
        if (!usable.test(executors)) {
            throw new DmsException(message, HttpStatus.CONFLICT);
        }
        return executors;
    }

    // Dòng tạo trước FR-03 chưa có thời gian chờ: không tính được lịch, yêu cầu lưu lại cấu hình.
    private void assertScheduleComplete(DmsConfig config) {
        if (config.getCheckinIntervalDays() == null || config.getGracePeriodDays() == null
                || config.getReminderCount() == null) {
            throw new DmsException(INCOMPLETE_CONFIG_MESSAGE, HttpStatus.CONFLICT);
        }
    }

    // Chu kỳ mới bắt đầu từ now: nextCheckinDueAt là hạn chót trigger (triggerAt), không phải hạn check-in.
    private void startNewCycle(DmsConfig config, LocalDateTime now) {
        DmsSchedule schedule = scheduleCalculator.calculate(now, config.getCheckinIntervalDays(),
                config.getGracePeriodDays(), config.getReminderCount());
        config.setLastCheckinAt(now);
        config.setRemindersSent(0);
        config.setTriggerDeferrals(0);
        config.setNextCheckinDueAt(schedule.triggerAt());
        config.setNextReminderAt(schedule.nextReminderAt(0).orElse(null));
    }

    // vaultId = null: DMS gắn với Owner, không với một Vault cụ thể.
    private void audit(AuditAction action, User owner, DmsConfig config, String detail) {
        auditLogService.log(action, AuditResult.SUCCESS, owner.getId(), owner.getEmail(), null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG, config.getId().toString(),
                DmsActivationProcessor.ownerDetail(owner.getId()) + ", " + detail);
    }

    // Actor là Admin; email là của chính Admin, không ghi email Owner vào detail.
    private void auditByAdmin(AuditAction action, UUID adminId, DmsConfig config, String detail) {
        String adminEmail = userRepository.findById(adminId).map(User::getEmail).orElse(null);
        String ownerDetail = DmsActivationProcessor.ownerDetail(config.getOwner().getId());
        auditLogService.log(action, AuditResult.SUCCESS, adminId, adminEmail, null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG, config.getId().toString(),
                detail == null ? ownerDetail : ownerDetail + ", " + detail);
    }

    private DmsConfigResponse toResponse(DmsConfig config) {
        return toResponse(config, executorAssignmentRepository.findAllByOwnerId(config.getOwner().getId()));
    }

    private DmsConfigResponse toResponse(DmsConfig config, List<ExecutorAssignment> executors) {
        LocalDateTime checkinDueAt = config.getLastCheckinAt() != null && config.getCheckinIntervalDays() != null
                ? config.getLastCheckinAt().plusDays(config.getCheckinIntervalDays())
                : null;
        return DmsConfigResponse.builder()
                .status(config.getStatus())
                .checkinIntervalDays(config.getCheckinIntervalDays())
                .gracePeriodDays(config.getGracePeriodDays())
                .reminderCount(config.getReminderCount())
                .notificationChannel(config.getNotificationChannel())
                .lastCheckinAt(config.getLastCheckinAt())
                .checkinDueAt(checkinDueAt)
                .triggerAt(config.getNextCheckinDueAt())
                .nextReminderAt(config.getNextReminderAt())
                .remindersSent(config.getRemindersSent())
                .triggeredAt(config.getTriggeredAt())
                .hasUsableExecutor(config.getStatus() == DmsStatus.TRIGGERED
                        ? null
                        : ExecutorActivationRule.pickActivationTarget(executors).isPresent())
                .build();
    }
}
