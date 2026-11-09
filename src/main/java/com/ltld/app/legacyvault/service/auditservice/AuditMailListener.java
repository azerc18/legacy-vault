package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.event.AssetReverificationNeededEvent;
import com.ltld.app.legacyvault.event.ExecutorTaskCompletedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptPdfService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptService;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gửi mail theo sự kiện từ audit log (D.2 của kế hoạch). Mọi hành động đã được ghi qua AuditLogService đều phát
 * AuditEvent; listener này chỉ ĐỌC sự kiện và gửi mail sau khi transaction đã commit, nên không phải sửa service
 * của người khác và không bao giờ gửi mail cho việc đã bị rollback.
 *
 * <p>Chạy @Async; lỗi gửi chỉ được log warn, không ảnh hưởng nghiệp vụ. Chỉ thêm case mới vào {@link #handle}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditMailListener {

    private static final Pattern LOCKED_UNTIL = Pattern.compile("locked until (\\S+)");
    private static final Pattern NEXT_CHECKIN = Pattern.compile("nextCheckinDueAt=([^,\\s]+)");
    private static final Pattern DMS_SETTINGS = Pattern.compile(
            "checkinIntervalDays=(\\d+), gracePeriodDays=(\\d+), reminderCount=(\\d+)");
    private static final Pattern OWNER_ID = Pattern.compile("ownerId=([0-9a-fA-F-]{36})");
    private static final Pattern REASON = Pattern.compile("reason=(.+)$");
    private static final Pattern STATUS_CHANGE = Pattern.compile("^STATUS (\\w+)->(\\w+)");
    private static final Pattern VAULT_NAME = Pattern.compile("name=(.+)$");

    private final PaymentReceiptService receiptService;
    private final PaymentReceiptPdfService receiptPdfService;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailSender emailSender;
    private final UserRepository userRepository;
    private final VaultRepository vaultRepository;
    private final MailDataReader mailData;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AuditEvent event) {
        try {
            handle(event);
        } catch (RuntimeException e) {
            log.warn("Event mail for {} failed: {}", event.action(), e.getMessage());
        }
    }

    // N18: xác thực tài sản sắp hết hạn / đã hết hạn
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssetReverificationNeeded(AssetReverificationNeededEvent e) {
        try {
            userRepository.findById(e.ownerId()).ifPresent(u -> emailSender.sendAssetReverificationNotice(
                    u.getEmail(), u.getFullName(), e.assetName(), e.expiresAt(), e.expired(), e.vaultId()));
        } catch (RuntimeException ex) {
            log.warn("N18: mail for asset {} failed: {}", e.assetId(), ex.getMessage());
        }
    }

    // N29: Executor báo xong một việc
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecutorTaskCompleted(ExecutorTaskCompletedEvent e) {
        try {
            mailData.completedTask(e.taskId()).ifPresent(task -> task.recipients().forEach(r ->
                    emailSender.sendExecutorTaskCompletedNotice(r.email(), r.fullName(), task.vaultName(),
                            task.platform(), e.hasEvidenceFile(), e.vaultId())));
        } catch (RuntimeException ex) {
            log.warn("N29: mail for task {} failed: {}", e.taskId(), ex.getMessage());
        }
    }

    void handle(AuditEvent e) {
        switch (e.action()) {
            // N1: xác minh email xong
            case EMAIL_VERIFIED -> {
                if (isSuccess(e)) {
                    actor(e).ifPresent(u -> emailSender.sendWelcomeEmail(u.getEmail(), u.getFullName()));
                }
            }
            // N2: đặt lại mật khẩu thành công
            case PASSWORD_RESET_SUCCESS -> {
                if (isSuccess(e)) {
                    actor(e).ifPresent(u -> emailSender.sendPasswordChangedNotice(u.getEmail(), u.getFullName()));
                }
            }
            // N3: khóa tạm do sai nhiều lần (detail "locked until <Instant> after ..."); các nơi khác ghi
            // ACCOUNT_LOCKED với chữ "account locked" là lần thử khi đang bị khóa nên không gửi lại.
            case ACCOUNT_LOCKED -> {
                String detail = detail(e);
                if (detail != null && detail.startsWith("locked until ")) {
                    int minutes = lockMinutes(detail);
                    actor(e).ifPresent(u -> emailSender.sendAccountLockedNotice(u.getEmail(), u.getFullName(), minutes));
                }
            }
            // N19: check-in thành công (trong app hoặc bằng link một chạm)
            case DMS_CHECKIN, DMS_CHECKIN_ONE_CLICK -> {
                if (isSuccess(e)) {
                    LocalDateTime next = nextCheckin(detail(e));
                    actor(e).ifPresent(u -> emailSender.sendDmsCheckinConfirmation(u.getEmail(), u.getFullName(), next));
                }
            }
            // N14: két sắt bị xóa (detail "name=<tên>")
            case VAULT_DELETED -> {
                if (isSuccess(e)) {
                    String name = first(VAULT_NAME, detail(e));
                    actor(e).ifPresent(u -> emailSender.sendVaultDeletedNotice(u.getEmail(), u.getFullName(),
                            name == null ? "" : name));
                }
            }
            // N21: lưu cấu hình Công tắc người chết
            case DMS_CONFIGURED -> {
                String detail = detail(e);
                Matcher m = detail == null ? null : DMS_SETTINGS.matcher(detail);
                if (isSuccess(e) && m != null && m.find()) {
                    int checkin = Integer.parseInt(m.group(1));
                    int grace = Integer.parseInt(m.group(2));
                    int reminders = Integer.parseInt(m.group(3));
                    actor(e).ifPresent(u -> emailSender.sendDmsConfiguredNotice(
                            u.getEmail(), u.getFullName(), checkin, grace, reminders));
                }
            }
            // N22: admin tạm dừng / tiếp tục Công tắc người chết; actor là Admin, Owner nằm trong detail (ownerId=...)
            case DMS_SUSPENDED, DMS_RESUMED -> {
                boolean paused = e.action() == com.ltld.app.legacyvault.enums.AuditAction.DMS_SUSPENDED;
                String detail = detail(e);
                String reason = paused ? first(REASON, detail) : null;
                user(first(OWNER_ID, detail)).ifPresent(u ->
                        emailSender.sendDmsAdminActionNotice(u.getEmail(), u.getFullName(), paused, reason));
            }
            // N9 + N11: entityId là giao dịch, chung cho mọi cổng thanh toán
            case PAYMENT_SUCCEEDED -> {
                if (isSuccess(e) && e.entityId() != null) {
                    actor(e).ifPresent(u -> handlePaymentSucceeded(e, u));
                }
            }
            // N10
            case PAYMENT_FAILED -> actor(e).ifPresent(u ->
                    emailSender.sendPaymentFailedNotice(u.getEmail(), u.getFullName()));
            // N26: người thụ hưởng đã đóng phần nhận
            case CLAIM_COMPLETED -> {
                if (isSuccess(e) && e.vaultId() != null) {
                    String vaultName = vaultRepository.findById(e.vaultId()).map(v -> v.getName()).orElse("");
                    actor(e).ifPresent(u -> emailSender.sendClaimCompletedNotice(
                            u.getEmail(), u.getFullName(), vaultName));
                }
            }
            // N4 / N5: admin đổi trạng thái hoặc vai trò của một người dùng (entityType "User", entityId = người đó)
            case RBAC_CHANGE -> {
                if (isSuccess(e) && "User".equals(e.entityType()) && e.entityId() != null) {
                    handleUserAdminChange(e);
                }
            }
            // N6: đăng nhập từ thiết bị chưa từng dùng
            case LOGIN -> {
                if (isSuccess(e) && e.actorId() != null) {
                    mailData.newDeviceOf(e.actorId()).ifPresent(device -> actor(e).ifPresent(u ->
                            emailSender.sendNewDeviceLoginNotice(u.getEmail(), u.getFullName(), device)));
                }
            }
            // N16: audit ghi chung VAULT_UPDATED, chỉ phân biệt thư di chúc qua detail
            case VAULT_UPDATED -> {
                String detail = detail(e);
                boolean created = detail != null && detail.startsWith("Created legacy message:");
                boolean updated = detail != null && detail.startsWith("Updated legacy message:");
                if (isSuccess(e) && (created || updated) && e.vaultId() != null) {
                    String vaultName = vaultRepository.findById(e.vaultId()).map(v -> v.getName()).orElse("");
                    actor(e).ifPresent(u -> emailSender.sendLegacyMessageSavedNotice(
                            u.getEmail(), u.getFullName(), vaultName, updated));
                }
            }
            // N18: tài sản vừa xác thực xong
            case ASSET_VERIFICATION_VERIFIED -> {
                if (isSuccess(e) && e.entityId() != null) {
                    mailData.assetName(e.entityId()).ifPresent(name -> actor(e).ifPresent(u ->
                            emailSender.sendAssetVerifiedNotice(u.getEmail(), u.getFullName(), name)));
                }
            }
            // N27: két lưu trữ vì quá hạn nhận
            case VAULT_ARCHIVED -> {
                if (isSuccess(e) && e.vaultId() != null) {
                    mailData.vaultRecipients(e.vaultId()).ifPresent(v -> v.recipients().forEach(r ->
                            emailSender.sendVaultArchivedNotice(r.email(), r.fullName(), v.vaultName())));
                }
            }
            // N30: hồ sơ được mở lại
            case LEGAL_REQUEST_REOPENED -> {
                if (isSuccess(e) && e.entityId() != null) {
                    mailData.reopenedRequest(e.entityId()).ifPresent(r ->
                            emailSender.sendVerificationReopenedNotice(r.executorEmail(), r.ownerLabel()));
                }
            }
            default -> {
                // các hành động khác không gửi mail ở đây
            }
        }
    }

    private void handlePaymentSucceeded(AuditEvent e, User u) {
        PaymentReceiptData receipt = receiptService.getReceiptData(e.entityId(), e.actorId());
        byte[] pdf = null;
        try {
            pdf = receiptPdfService.render(receipt);
        } catch (RuntimeException ex) {
            log.warn("N9: could not render receipt for payment {}: {}", e.entityId(), ex.getMessage());
        }
        emailSender.sendPaymentSucceededNotice(u.getEmail(), u.getFullName(), receipt.planName(), receipt.amountVnd(),
                receipt.receiptNo(), pdf);
        emailSender.sendVipActivatedNotice(u.getEmail(), u.getFullName(), receipt.planName(),
                subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(e.actorId(), SubscriptionStatus.ACTIVE)
                        .map(Subscription::getExpiresAt).orElse(null));
    }

    private void handleUserAdminChange(AuditEvent e) {
        String detail = detail(e);
        if (detail == null) {
            return;
        }
        Optional<User> target = userRepository.findById(e.entityId());
        if (target.isEmpty()) {
            return;
        }
        User u = target.get();
        Matcher status = STATUS_CHANGE.matcher(detail);
        if (status.find()) {
            String from = status.group(1);
            String to = status.group(2);
            if ("LOCKED".equals(to)) {
                emailSender.sendAccountStatusNotice(u.getEmail(), u.getFullName(), true, first(REASON, detail));
            } else if ("LOCKED".equals(from) && "ACTIVE".equals(to)) {
                emailSender.sendAccountStatusNotice(u.getEmail(), u.getFullName(), false, null);
            }
        } else if (detail.startsWith("GRANT ")) {
            emailSender.sendRoleChangedNotice(u.getEmail(), u.getFullName(), true, detail.substring(6).trim());
        } else if (detail.startsWith("REVOKE ")) {
            emailSender.sendRoleChangedNotice(u.getEmail(), u.getFullName(), false, detail.substring(7).trim());
        }
    }

    private Optional<User> user(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return userRepository.findById(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static String first(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }

    private Optional<User> actor(AuditEvent e) {
        return e.actorId() == null ? Optional.empty() : userRepository.findById(e.actorId());
    }

    private static boolean isSuccess(AuditEvent e) {
        Map<String, Object> m = e.metadata();
        return m != null && AuditResult.SUCCESS.name().equals(m.get("result"));
    }

    private static String detail(AuditEvent e) {
        Object d = e.metadata() == null ? null : e.metadata().get("detail");
        return d == null ? null : d.toString();
    }

    static int lockMinutes(String detail) {
        Matcher m = LOCKED_UNTIL.matcher(detail);
        if (m.find()) {
            try {
                long seconds = Duration.between(Instant.now(), Instant.parse(m.group(1))).getSeconds();
                return (int) Math.max(1, Math.round(seconds / 60.0));
            } catch (DateTimeParseException ignored) {
                // rơi xuống giá trị mặc định
            }
        }
        return 15;
    }

    static LocalDateTime nextCheckin(String detail) {
        if (detail == null) {
            return null;
        }
        Matcher m = NEXT_CHECKIN.matcher(detail);
        if (m.find()) {
            try {
                return LocalDateTime.parse(m.group(1));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }
}
