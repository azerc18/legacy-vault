package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.SecurityAlertRule;
import com.ltld.app.legacyvault.enums.SecurityAlertSeverity;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.SecurityAlertRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * FR-20: quét audit log theo luật, tạo SecurityAlert và mail Admin khi mức HIGH.
 * - Luật sự kiện: mỗi audit log tạo tối đa 1 alert (subject = id log), không trùng dù cửa sổ quét chồng nhau.
 * - Luật đếm: một đợt tấn công chỉ có 1 alert OPEN cho (luật, IP/user); Admin acknowledge rồi mới tạo alert mới.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAlertDetector {

    static final Duration EVENT_LOOKBACK = Duration.ofMinutes(15);
    static final String ADMIN_ROLE = "ADMIN";
    private static final Set<AuditAction> EVENT_ACTIONS =
            EnumSet.of(AuditAction.ACCOUNT_LOCKED, AuditAction.TOKEN_REUSE_DETECTED, AuditAction.RBAC_CHANGE);
    private static final Set<AuditAction> DOWNLOAD_ACTIONS =
            EnumSet.of(AuditAction.ASSET_DOWNLOADED, AuditAction.DOCUMENT_DOWNLOADED);

    private final AuditLogRepository auditLogRepository;
    private final SecurityAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ExecutorNotificationService notificationService;

    @Value("${app.security-alerts.login-failures-per-ip:10}")
    private long loginFailuresPerIp;
    @Value("${app.security-alerts.login-failure-window:PT15M}")
    private Duration loginFailureWindow;
    @Value("${app.security-alerts.downloads-per-user:20}")
    private long downloadsPerUser;
    @Value("${app.security-alerts.download-window:PT10M}")
    private Duration downloadWindow;

    /** @return số alert mới được tạo */
    @Transactional
    public int detect(Instant now) {
        List<SecurityAlert> created = new ArrayList<>();
        detectEvents(now, created);
        detectLoginFailuresByIp(now, created);
        detectMassDownloads(now, created);
        notifyAdminsAfterCommit(created);
        return created.size();
    }

    private void detectEvents(Instant now, List<SecurityAlert> created) {
        List<AuditLog> logs = auditLogRepository.findByActionInAndCreatedAtAfter(EVENT_ACTIONS, now.minus(EVENT_LOOKBACK));
        for (AuditLog log : logs) {
            SecurityAlertRule rule = ruleFor(log);
            if (rule == null) continue;
            String subject = log.getId().toString();
            if (alertRepository.existsByRuleAndSubject(rule, subject)) continue;
            Instant at = log.getCreatedAt() != null ? log.getCreatedAt() : now;
            created.add(save(rule, subject, eventMessage(rule, log), 1, at, at));
        }
    }

    private void detectLoginFailuresByIp(Instant now, List<SecurityAlert> created) {
        List<AuditLogRepository.IpCount> rows = auditLogRepository.countByIpSince(
                AuditAction.LOGIN_FAILED, now.minus(loginFailureWindow), loginFailuresPerIp);
        for (AuditLogRepository.IpCount row : rows) {
            String msg = String.format("%d lần đăng nhập thất bại từ IP %s trong %d phút gần nhất.",
                    row.getTotal(), row.getIp(), loginFailureWindow.toMinutes());
            upsertOpen(SecurityAlertRule.LOGIN_FAILURES_FROM_IP, row.getIp(), msg, row.getTotal(), now, created);
        }
    }

    private void detectMassDownloads(Instant now, List<SecurityAlert> created) {
        List<AuditLogRepository.ActorCount> rows = auditLogRepository.countByActorSince(
                DOWNLOAD_ACTIONS, now.minus(downloadWindow), downloadsPerUser);
        for (AuditLogRepository.ActorCount row : rows) {
            String who = userRepository.findById(row.getActorId()).map(User::getEmail)
                    .orElse(row.getActorId().toString());
            String msg = String.format("%s đã tải %d tài sản/tài liệu trong %d phút gần nhất.",
                    who, row.getTotal(), downloadWindow.toMinutes());
            upsertOpen(SecurityAlertRule.MASS_DOWNLOAD, row.getActorId().toString(), msg, row.getTotal(), now, created);
        }
    }

    private void upsertOpen(SecurityAlertRule rule, String subject, String msg, long count, Instant now,
                            List<SecurityAlert> created) {
        Optional<SecurityAlert> open = alertRepository.findFirstByRuleAndSubjectAndStatus(
                rule, subject, SecurityAlertStatus.OPEN);
        if (open.isPresent()) {
            SecurityAlert alert = open.get();   // managed entity, lưu khi commit
            alert.setEventCount(Math.max(alert.getEventCount(), (int) count));
            alert.setLastSeenAt(now);
            alert.setMessage(truncate(msg));
            return;
        }
        created.add(save(rule, subject, msg, (int) count, now, now));
    }

    private SecurityAlert save(SecurityAlertRule rule, String subject, String msg, int count,
                               Instant first, Instant last) {
        return alertRepository.save(SecurityAlert.builder()
                .rule(rule)
                .severity(rule.getSeverity())
                .status(SecurityAlertStatus.OPEN)
                .subject(subject)
                .message(truncate(msg))
                .eventCount(count)
                .firstSeenAt(first)
                .lastSeenAt(last)
                .build());
    }

    private static SecurityAlertRule ruleFor(AuditLog log) {
        return switch (log.getAction()) {
            case ACCOUNT_LOCKED -> SecurityAlertRule.ACCOUNT_LOCKED;
            case TOKEN_REUSE_DETECTED -> SecurityAlertRule.REFRESH_TOKEN_REUSE;
            // AdminUserServiceImpl ghi detail "GRANT <ROLE>"
            case RBAC_CHANGE -> "GRANT ADMIN".equals(meta(log, "detail")) ? SecurityAlertRule.ADMIN_ROLE_GRANTED : null;
            default -> null;
        };
    }

    private String eventMessage(SecurityAlertRule rule, AuditLog log) {
        String ip = log.getIpAddress() != null ? log.getIpAddress() : "không rõ";
        String actor = log.getActor() != null ? log.getActor().getEmail() : meta(log, "email");
        return switch (rule) {
            case ACCOUNT_LOCKED -> String.format(
                    "Tài khoản %s bị khoá tạm sau nhiều lần sai mật khẩu (IP %s).", orUnknown(actor), ip);
            case REFRESH_TOKEN_REUSE -> String.format(
                    "Refresh token cũ của %s bị dùng lại (IP %s): có thể token đã bị đánh cắp, mọi phiên đã bị thu hồi.",
                    orUnknown(actor), ip);
            case ADMIN_ROLE_GRANTED -> {
                UUID targetId = log.getEntityId();
                String target = targetId == null ? "không rõ"
                        : userRepository.findById(targetId).map(User::getEmail).orElse(targetId.toString());
                yield String.format("%s đã cấp quyền ADMIN cho %s.", orUnknown(actor), target);
            }
            default -> rule.name();
        };
    }

    // Mail chỉ gửi sau khi alert đã lưu; lỗi gửi chỉ ghi log (giống B6 trong DmsActivationProcessor)
    private void notifyAdminsAfterCommit(List<SecurityAlert> created) {
        List<String> messages = created.stream()
                .filter(a -> a.getSeverity() == SecurityAlertSeverity.HIGH)
                .map(a -> "[LegacyVault] Cảnh báo bảo mật " + a.getRule() + ": " + a.getMessage())
                .toList();
        if (messages.isEmpty()) return;

        AfterCommit.run(() -> {
            List<User> admins;
            try {
                admins = userRepository.findActiveByRoleCode(ADMIN_ROLE);
            } catch (RuntimeException e) {
                log.warn("Security alert: cannot load admins to notify", e);
                return;
            }
            for (User admin : admins) {
                for (String content : messages) {
                    try {
                        notificationService.send(admin.getEmail(), content);
                    } catch (RuntimeException e) {
                        log.warn("Security alert: notifying admin {} failed: {}", admin.getId(), e.getMessage());
                    }
                }
            }
        });
    }

    private static String meta(AuditLog log, String key) {
        Map<String, Object> m = log.getMetadata();
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String orUnknown(String s) {
        return s == null ? "không rõ" : s;
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}