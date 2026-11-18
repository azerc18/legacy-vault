package com.ltld.app.legacyvault.service.systempolicyservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.systempolicydto.AdminDmsPolicyResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.DmsPolicy;
import com.ltld.app.legacyvault.dto.systempolicydto.DmsPolicy.Range;
import com.ltld.app.legacyvault.dto.systempolicydto.UpdateDmsPolicyRequest;
import com.ltld.app.legacyvault.dto.systempolicydto.UpdateDmsPolicyRequest.RangeInput;
import com.ltld.app.legacyvault.entity.DmsPolicySetting;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsPolicySettingRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chính sách Dead Man's Switch do Admin chỉnh (trang "Chính sách hệ thống"):
 * <ul>
 *   <li>khoảng cho phép + giá trị gợi ý của chu kỳ check-in, thời gian chờ, số lần nhắc;</li>
 *   <li>số ngày chờ trước khi chuyển sang Executor dự phòng (BR-03), áp dụng cho DMS bật MỚI.</li>
 * </ul>
 * Giới hạn cứng {@link DmsPolicy#HARD_CHECKIN_INTERVAL} ... vẫn do {@code DmsConfigRequest} kiểm; ở đây Admin chỉ thu
 * hẹp trong khoảng đó. DMS đang chạy với giá trị ngoài khoảng mới KHÔNG bị sửa hay dừng: chỉ khi Owner lưu lại cấu hình
 * mới phải chọn giá trị nằm trong khoảng.
 *
 * <p>Đọc lại DB tối đa {@link #CACHE_TTL_MS} một lần như {@code AiSettingsService}, làm mới ngay sau khi Admin lưu
 * (sau commit). Đọc DB lỗi thì dùng {@link DmsPolicy#DEFAULTS} (đúng hành vi trước khi có trang này).
 */
@Slf4j
@Service
public class DmsPolicyService {

    static final long CACHE_TTL_MS = 30_000;

    private final DmsPolicySettingRepository repository;
    private final DmsConfigRepository dmsConfigRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    private volatile DmsPolicy cache;
    private volatile long cacheLoadedAt;

    public DmsPolicyService(DmsPolicySettingRepository repository, DmsConfigRepository dmsConfigRepository,
                            UserRepository userRepository, AuditLogService auditLogService, Clock clock) {
        this.repository = repository;
        this.dmsConfigRepository = dmsConfigRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Chính sách đang có hiệu lực (có cache). Dùng khi Owner lưu DMS, khi tạo DMS mới, và cho form của Owner. */
    public DmsPolicy current() {
        DmsPolicy c = cache;
        long now = clock.millis();
        if (c != null && now - cacheLoadedAt < CACHE_TTL_MS) {
            return c;
        }
        try {
            c = repository.findById(DmsPolicySetting.SINGLETON_ID).map(DmsPolicyService::toPolicy)
                    .orElse(DmsPolicy.DEFAULTS);
        } catch (RuntimeException e) {
            log.warn("Could not read DMS policy, using defaults: {}", e.getClass().getSimpleName());
            c = c != null ? c : DmsPolicy.DEFAULTS;
        }
        cache = c;
        cacheLoadedAt = now;
        return c;
    }

    /**
     * Owner lưu cấu hình DMS: từng giá trị phải nằm trong khoảng Admin cho phép. Lỗi trả 400 DMS_POLICY_OUT_OF_RANGE
     * kèm tên ô và khoảng, để FE hiện đúng ô sai.
     */
    public void requireWithinPolicy(DmsConfigRequest request) {
        DmsPolicy p = current();
        check("Check-in interval", request.getCheckinIntervalDays(), p.checkinIntervalDays(), "days");
        check("Grace period", request.getGracePeriodDays(), p.gracePeriodDays(), "days");
        check("Reminder count", request.getReminderCount(), p.reminderCount(), "");
    }

    @Transactional(readOnly = true)
    public AdminDmsPolicyResponse getForAdmin() {
        DmsPolicySetting row = repository.findById(DmsPolicySetting.SINGLETON_ID).orElse(null);
        DmsPolicy policy = row == null ? DmsPolicy.DEFAULTS : toPolicy(row);
        String email = row == null || row.getUpdatedBy() == null ? null
                : userRepository.findById(row.getUpdatedBy()).map(User::getEmail).orElse(null);
        return new AdminDmsPolicyResponse(policy, hardLimits(), countOutside(policy),
                row == null ? null : row.getUpdatedAt(), email);
    }

    @Transactional
    public AdminDmsPolicyResponse update(UUID adminId, UpdateDmsPolicyRequest req) {
        Range interval = validate("Check-in interval", req.checkinIntervalDays(), DmsPolicy.HARD_CHECKIN_INTERVAL);
        Range grace = validate("Grace period", req.gracePeriodDays(), DmsPolicy.HARD_GRACE_PERIOD);
        Range reminders = validate("Reminder count", req.reminderCount(), DmsPolicy.HARD_REMINDER_COUNT);
        int backupWait = req.backupActivationWaitDays();
        if (backupWait < DmsPolicy.HARD_BACKUP_WAIT_MIN || backupWait > DmsPolicy.HARD_BACKUP_WAIT_MAX) {
            throw new BusinessException(ErrorCode.SYSTEM_POLICY_INVALID, "Backup executor wait must be between "
                    + DmsPolicy.HARD_BACKUP_WAIT_MIN + " and " + DmsPolicy.HARD_BACKUP_WAIT_MAX + " days.");
        }

        DmsPolicySetting row = repository.findById(DmsPolicySetting.SINGLETON_ID)
                .orElseGet(() -> DmsPolicySetting.builder().id(DmsPolicySetting.SINGLETON_ID).build());
        DmsPolicy before = toPolicy(row);
        DmsPolicy after = new DmsPolicy(interval, grace, reminders, backupWait);
        List<String> changes = diff(before, after);
        if (!changes.isEmpty()) {
            row.setCheckinIntervalMinDays(interval.min());
            row.setCheckinIntervalDefaultDays(interval.defaultValue());
            row.setCheckinIntervalMaxDays(interval.max());
            row.setGracePeriodMinDays(grace.min());
            row.setGracePeriodDefaultDays(grace.defaultValue());
            row.setGracePeriodMaxDays(grace.max());
            row.setReminderCountMin(reminders.min());
            row.setReminderCountDefault(reminders.defaultValue());
            row.setReminderCountMax(reminders.max());
            row.setBackupActivationWaitDays(backupWait);
            row.setUpdatedBy(adminId);
            row.setUpdatedAt(LocalDateTime.now(clock));
            repository.save(row);
            String email = userRepository.findById(adminId).map(User::getEmail).orElse(null);
            auditLogService.log(AuditAction.SYSTEM_POLICY_UPDATED, AuditResult.SUCCESS, adminId, email,
                    "DmsPolicySetting", null, "DMS: " + String.join("; ", changes));
            log.info("DMS policy changed by admin {}: {}", adminId, changes);
            invalidateCacheAfterCommit();
        }
        String email = row.getUpdatedBy() == null ? null
                : userRepository.findById(row.getUpdatedBy()).map(User::getEmail).orElse(null);
        return new AdminDmsPolicyResponse(after, hardLimits(), countOutside(after), row.getUpdatedAt(), email);
    }

    // ------------------------------------------------------------------ nội bộ

    static DmsPolicy hardLimits() {
        return new DmsPolicy(DmsPolicy.HARD_CHECKIN_INTERVAL, DmsPolicy.HARD_GRACE_PERIOD,
                DmsPolicy.HARD_REMINDER_COUNT, DmsPolicy.HARD_BACKUP_WAIT_MAX);
    }

    private long countOutside(DmsPolicy p) {
        return dmsConfigRepository.countRunningOutsidePolicy(List.of(DmsStatus.ACTIVE, DmsStatus.PENDING_CONFIRMATION),
                p.checkinIntervalDays().min(), p.checkinIntervalDays().max(),
                p.gracePeriodDays().min(), p.gracePeriodDays().max(),
                p.reminderCount().min(), p.reminderCount().max());
    }

    private static void check(String field, Integer value, Range range, String unit) {
        if (value != null && !range.contains(value)) {
            String suffix = unit.isEmpty() ? "" : " " + unit;
            throw new DmsException(ErrorCode.DMS_POLICY_OUT_OF_RANGE, field + " must be between " + range.min()
                    + " and " + range.max() + suffix + " (system policy).");
        }
    }

    private static Range validate(String field, RangeInput in, Range hard) {
        int min = in.min();
        int def = in.defaultValue();
        int max = in.max();
        if (min < hard.min() || max > hard.max()) {
            throw new BusinessException(ErrorCode.SYSTEM_POLICY_INVALID,
                    field + ": the range must stay within " + hard.min() + "–" + hard.max() + ".");
        }
        if (min > max) {
            throw new BusinessException(ErrorCode.SYSTEM_POLICY_INVALID, field + ": minimum is greater than maximum.");
        }
        if (def < min || def > max) {
            throw new BusinessException(ErrorCode.SYSTEM_POLICY_INVALID,
                    field + ": the default value must be between the minimum and the maximum.");
        }
        return new Range(min, def, max);
    }

    private static List<String> diff(DmsPolicy a, DmsPolicy b) {
        List<String> out = new ArrayList<>();
        diffRange(out, "checkinIntervalDays", a.checkinIntervalDays(), b.checkinIntervalDays());
        diffRange(out, "gracePeriodDays", a.gracePeriodDays(), b.gracePeriodDays());
        diffRange(out, "reminderCount", a.reminderCount(), b.reminderCount());
        if (a.backupActivationWaitDays() != b.backupActivationWaitDays()) {
            out.add("backupActivationWaitDays " + a.backupActivationWaitDays() + " -> " + b.backupActivationWaitDays());
        }
        return out;
    }

    private static void diffRange(List<String> out, String name, Range a, Range b) {
        if (!a.equals(b)) {
            out.add(name + " " + fmt(a) + " -> " + fmt(b));
        }
    }

    private static String fmt(Range r) {
        return r.min() + ".." + r.max() + " (default " + r.defaultValue() + ")";
    }

    static DmsPolicy toPolicy(DmsPolicySetting s) {
        return new DmsPolicy(
                new Range(s.getCheckinIntervalMinDays(), s.getCheckinIntervalDefaultDays(), s.getCheckinIntervalMaxDays()),
                new Range(s.getGracePeriodMinDays(), s.getGracePeriodDefaultDays(), s.getGracePeriodMaxDays()),
                new Range(s.getReminderCountMin(), s.getReminderCountDefault(), s.getReminderCountMax()),
                s.getBackupActivationWaitDays());
    }

    /** Như AiSettingsService: xoá cache SAU commit để lời gọi chen giữa không giữ lại giá trị cũ thêm 30 giây. */
    private void invalidateCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache = null;
                }
            });
        } else {
            cache = null;
        }
    }
}
