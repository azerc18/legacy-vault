package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.repository.UserRepository;
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

    private final EmailSender emailSender;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AuditEvent event) {
        try {
            handle(event);
        } catch (RuntimeException e) {
            log.warn("Event mail for {} failed: {}", event.action(), e.getMessage());
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
            default -> {
                // các hành động khác không gửi mail ở đây
            }
        }
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
