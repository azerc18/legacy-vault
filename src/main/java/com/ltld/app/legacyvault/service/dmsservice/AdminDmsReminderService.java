package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.AdminDmsReminderResponse;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotifier;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Admin chủ động nhắc một Owner check-in DMS (ngoài lịch nhắc tự động FR-03).
 * Ghi mốc nhắc trong transaction riêng (DmsConfigService.markAdminReminder, có khóa và cooldown), commit xong mới
 * ghi chuông và gửi mail kèm link một chạm + QR như mail nhắc tự động. Không đổi remindersSent / nextReminderAt,
 * nên lịch nhắc tự động và luật hoãn Q27 giữ nguyên.
 * Gửi mail lỗi: chuông vẫn còn, lượt nhắc được trả lại để Admin bấm lại ngay, kết quả báo emailSent = false.
 */
@Slf4j
@Service
public class AdminDmsReminderService {

    private final DmsConfigService dmsConfigService;
    private final DmsOneClickCheckinService oneClickCheckinService;
    private final EmailSender emailSender;
    private final InAppNotifier inAppNotifier;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final long cooldownMinutes;

    public AdminDmsReminderService(DmsConfigService dmsConfigService,
                                   DmsOneClickCheckinService oneClickCheckinService,
                                   EmailSender emailSender, InAppNotifier inAppNotifier,
                                   AuditLogService auditLogService, UserRepository userRepository,
                                   @Value("${app.dms.admin-reminder-cooldown-minutes:60}") long cooldownMinutes) {
        this.dmsConfigService = dmsConfigService;
        this.oneClickCheckinService = oneClickCheckinService;
        this.emailSender = emailSender;
        this.inAppNotifier = inAppNotifier;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.cooldownMinutes = cooldownMinutes;
    }

    public long cooldownMinutes() {
        return cooldownMinutes;
    }

    /** Lần sớm nhất Admin được nhắc lại; null = được nhắc ngay. */
    public LocalDateTime nextAllowedAt(LocalDateTime lastAdminReminderAt) {
        if (lastAdminReminderAt == null || cooldownMinutes <= 0) {
            return null;
        }
        LocalDateTime next = lastAdminReminderAt.plusMinutes(cooldownMinutes);
        return next.isAfter(LocalDateTime.now()) ? next : null;
    }

    public AdminDmsReminderResponse remind(UUID ownerId, UUID adminId) {
        // MICROS: cột DATETIME(6); releaseAdminReminder so sánh đúng giá trị đã lưu.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        DmsConfigService.AdminReminderTarget target = dmsConfigService.markAdminReminder(ownerId, now, cooldownMinutes);

        inAppNotifier.toEmail(target.ownerEmail(), InAppNotifier.draft(NotificationType.DMS_CHECKIN_REMINDER,
                InAppNotifier.REF_DMS_CONFIG, target.configId(),
                "An administrator reminds you to check in to confirm you are safe.",
                "variant", "admin", "dueAt", target.checkinDueAt(), "triggerAt", target.triggerAt()));

        boolean emailSent;
        try {
            String token = oneClickCheckinService.generateCheckinTokenForConfig(target.configId());
            emailSender.sendDmsAdminCheckinReminder(target.ownerEmail(), target.checkinDueAt(), target.triggerAt(),
                    target.status() == DmsStatus.PENDING_CONFIRMATION, token);
            emailSent = true;
        } catch (RuntimeException e) {
            log.error("Admin check-in reminder email to owner {} failed", ownerId, e);
            emailSent = false;
            try {
                dmsConfigService.releaseAdminReminder(ownerId, now);
            } catch (RuntimeException ex) {
                log.error("Could not release admin check-in reminder for owner {}", ownerId, ex);
            }
        }

        String adminEmail = userRepository.findById(adminId).map(User::getEmail).orElse(null);
        auditLogService.log(AuditAction.DMS_ADMIN_CHECKIN_REMINDER, AuditResult.SUCCESS, adminId, adminEmail, null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG, target.configId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", emailSent=" + emailSent);

        return AdminDmsReminderResponse.builder()
                .sentAt(now)
                .emailSent(emailSent)
                .nextAllowedAt(emailSent && cooldownMinutes > 0 ? now.plusMinutes(cooldownMinutes) : null)
                .checkinDueAt(target.checkinDueAt())
                .triggerAt(target.triggerAt())
                .build();
    }
}
