package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DmsConfigServiceImpl implements DmsConfigService {

    // Giữ backupActivationWaitDays hiện có; cấu hình mới dùng BR-03 (14 ngày).
    static final int DEFAULT_BACKUP_ACTIVATION_WAIT_DAYS = 14;

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    @Override
    @Transactional(readOnly = true)
    public DmsConfigResponse getConfig(UUID ownerId) {
        return toResponse(findConfig(ownerId));
    }

    @Override
    @Transactional
    public UpsertResult upsertConfig(UUID ownerId, DmsConfigRequest request) {
        DmsConfig existing = dmsConfigRepository.findByOwnerId(ownerId).orElse(null);
        // SRS: DMS đã kích hoạt thì quy trình bàn giao không thể bị Owner hủy hay cấu hình lại.
        if (existing != null && existing.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException("Dead Man's Switch đã được kích hoạt, không thể thay đổi cấu hình.",
                    HttpStatus.CONFLICT);
        }
        // FR-03 precondition: có ít nhất 1 Vault.
        if (!vaultRepository.existsByOwnerId(ownerId)) {
            throw new DmsException("Cần tạo ít nhất một Vault trước khi cấu hình Dead Man's Switch.",
                    HttpStatus.CONFLICT);
        }

        User owner = existing != null ? existing.getOwner() : userRepository.findById(ownerId)
                .orElseThrow(() -> new DmsException("Không tìm thấy Owner.", HttpStatus.NOT_FOUND));
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
                throw new DmsException("Cấu hình Dead Man's Switch vừa được tạo bởi một yêu cầu khác. Vui lòng tải lại.",
                        HttpStatus.CONFLICT);
            }
            throw e;
        }

        // Ghi audit SAU flush: audit vẫn được lưu khi rollback nên không được ghi cho thay đổi chưa chắc thành công.
        audit(AuditAction.DMS_CONFIGURED, owner, config, "created=" + created
                + ", checkinIntervalDays=" + config.getCheckinIntervalDays()
                + ", gracePeriodDays=" + config.getGracePeriodDays()
                + ", reminderCount=" + config.getReminderCount());
        return new UpsertResult(toResponse(config), created);
    }

    @Override
    @Transactional
    public DmsConfigResponse checkIn(UUID ownerId) {
        DmsConfig config = findConfig(ownerId);
        if (config.getStatus() != DmsStatus.ACTIVE) {
            // TRIGGERED: SRS không cho Owner hủy quy trình bàn giao đã kích hoạt bằng check-in.
            throw new DmsException(config.getStatus() == DmsStatus.TRIGGERED
                    ? "Dead Man's Switch đã được kích hoạt, không thể check-in."
                    : "Dead Man's Switch không ở trạng thái hoạt động.", HttpStatus.CONFLICT);
        }
        if (config.getCheckinIntervalDays() == null || config.getGracePeriodDays() == null
                || config.getReminderCount() == null) {
            // Dòng tạo trước FR-03 chưa có thời gian chờ: không tính được lịch, yêu cầu lưu lại cấu hình.
            throw new DmsException("Cấu hình Dead Man's Switch chưa đầy đủ, vui lòng lưu lại cấu hình.",
                    HttpStatus.CONFLICT);
        }

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

    private DmsConfig findConfig(UUID ownerId) {
        return dmsConfigRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new DmsException("Chưa cấu hình Dead Man's Switch.", HttpStatus.NOT_FOUND));
    }

    // Chu kỳ mới bắt đầu từ now: nextCheckinDueAt là hạn chót trigger (triggerAt), không phải hạn check-in.
    private void startNewCycle(DmsConfig config, LocalDateTime now) {
        DmsSchedule schedule = scheduleCalculator.calculate(now, config.getCheckinIntervalDays(),
                config.getGracePeriodDays(), config.getReminderCount());
        config.setLastCheckinAt(now);
        config.setRemindersSent(0);
        config.setNextCheckinDueAt(schedule.triggerAt());
        config.setNextReminderAt(schedule.nextReminderAt(0).orElse(null));
    }

    // vaultId = null: DMS gắn với Owner, không với một Vault cụ thể.
    private void audit(AuditAction action, User owner, DmsConfig config, String detail) {
        auditLogService.log(action, AuditResult.SUCCESS, owner.getId(), owner.getEmail(), null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG, config.getId().toString(),
                DmsActivationProcessor.ownerDetail(owner.getId()) + ", " + detail);
    }

    private DmsConfigResponse toResponse(DmsConfig config) {
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
                .build();
    }
}
