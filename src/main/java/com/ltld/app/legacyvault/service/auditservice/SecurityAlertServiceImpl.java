package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.SecurityAlertRule;
import com.ltld.app.legacyvault.enums.SecurityAlertSeverity;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.SecurityAlertRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecurityAlertServiceImpl implements SecurityAlertService {

    static final int MAX_NOTE_LENGTH = 500;

    private final SecurityAlertRepository alertRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    // AuditLogRepository cố ý chỉ cho ghi/tra cứu hẹp (append-only), nên đọc audit log theo id bằng JPQL
    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Page<SecurityAlertResponse> list(String status, String severity, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        SecurityAlertStatus st = parseStatus(status);
        SecurityAlertSeverity sev = parseSeverity(severity);
        Page<SecurityAlert> alerts = sev == null
                ? alertRepository.findByStatusOrderByCreatedAtDesc(st, pageable)
                : alertRepository.findByStatusAndSeverityOrderByCreatedAtDesc(st, sev, pageable);
        Map<UUID, User> related = relatedUsers(alerts.getContent());
        return alerts.map(a -> toResponse(a, related.get(a.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpen() {
        return alertRepository.countByStatus(SecurityAlertStatus.OPEN);
    }

    @Override
    @Transactional
    public SecurityAlertResponse acknowledge(UUID adminId, UUID alertId, String note) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AdminException("Alert not found", HttpStatus.NOT_FOUND));
        if (alert.getStatus() == SecurityAlertStatus.ACKNOWLEDGED) {
            throw new AdminException("The alert has already been acknowledged", HttpStatus.CONFLICT);
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > MAX_NOTE_LENGTH) {
            throw new AdminException("The note must be at most 500 characters", HttpStatus.BAD_REQUEST);
        }
        alert.setStatus(SecurityAlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(adminId);
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgeNote(cleanNote);
        SecurityAlert saved = alertRepository.saveAndFlush(alert);   // flush để @Version báo xung đột ngay

        auditLogService.log(AuditAction.SECURITY_ALERT_ACKNOWLEDGED, AuditResult.SUCCESS, adminId, null,
                "SecurityAlert", alertId.toString(), alert.getRule() + " " + alert.getSubject()
                        + (cleanNote == null ? "" : " note=" + cleanNote));
        return toResponse(saved, relatedUsers(List.of(saved)).get(saved.getId()));
    }

    /**
     * B3: người dùng liên quan tới từng cảnh báo (alertId -> User):
     * - MASS_DOWNLOAD: subject là id người dùng
     * - ACCOUNT_LOCKED / REFRESH_TOKEN_REUSE: subject là id audit log -> người thực hiện (hoặc email trong metadata)
     * - ADMIN_ROLE_GRANTED: subject là id audit log -> người được cấp ADMIN (entityId)
     * - LOGIN_FAILURES_FROM_IP: subject là IP, không có người dùng
     */
    private Map<UUID, User> relatedUsers(List<SecurityAlert> alerts) {
        Map<UUID, UUID> logIdByAlert = new HashMap<>();
        Map<UUID, UUID> userIdByAlert = new HashMap<>();
        for (SecurityAlert a : alerts) {
            if (a.getRule() == null) continue;
            switch (a.getRule()) {
                case MASS_DOWNLOAD -> parseUuid(a.getSubject()).ifPresent(id -> userIdByAlert.put(a.getId(), id));
                case ACCOUNT_LOCKED, REFRESH_TOKEN_REUSE, ADMIN_ROLE_GRANTED ->
                        parseUuid(a.getSubject()).ifPresent(id -> logIdByAlert.put(a.getId(), id));
                default -> { }
            }
        }
        Map<UUID, String> emailByAlert = new HashMap<>();
        if (!logIdByAlert.isEmpty()) {
            Map<UUID, AuditLog> logs = em.createQuery(
                            "SELECT l FROM AuditLog l LEFT JOIN FETCH l.actor WHERE l.id IN :ids", AuditLog.class)
                    .setParameter("ids", new HashSet<>(logIdByAlert.values()))
                    .getResultList().stream()
                    .collect(Collectors.toMap(AuditLog::getId, Function.identity()));
            for (SecurityAlert a : alerts) {
                AuditLog log = logs.get(logIdByAlert.get(a.getId()));
                if (log == null) continue;
                if (a.getRule() == SecurityAlertRule.ADMIN_ROLE_GRANTED) {
                    if (log.getEntityId() != null) userIdByAlert.put(a.getId(), log.getEntityId());
                } else if (log.getActor() != null) {
                    userIdByAlert.put(a.getId(), log.getActor().getId());
                } else if (log.getMetadata() != null && log.getMetadata().get("email") != null) {
                    emailByAlert.put(a.getId(), String.valueOf(log.getMetadata().get("email")));
                }
            }
        }
        Set<UUID> ids = new HashSet<>(userIdByAlert.values());
        Map<UUID, User> users = ids.isEmpty() ? Map.of() : userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, User> out = new HashMap<>();
        userIdByAlert.forEach((alertId, userId) -> {
            User u = users.get(userId);
            if (u != null) out.put(alertId, u);
        });
        emailByAlert.forEach((alertId, email) ->
                userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(u -> out.put(alertId, u)));
        return out;
    }

    private static Optional<UUID> parseUuid(String s) {
        try {
            return Optional.of(UUID.fromString(s));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static SecurityAlertStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return SecurityAlertStatus.OPEN;
        try {
            return SecurityAlertStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdminException("Invalid status (OPEN, ACKNOWLEDGED)", HttpStatus.BAD_REQUEST);
        }
    }

    private static SecurityAlertSeverity parseSeverity(String severity) {
        if (severity == null || severity.isBlank()) return null;
        try {
            return SecurityAlertSeverity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdminException("Invalid severity (HIGH, MEDIUM)", HttpStatus.BAD_REQUEST);
        }
    }

    private static SecurityAlertResponse toResponse(SecurityAlert a, User related) {
        return SecurityAlertResponse.builder()
                .id(a.getId()).rule(a.getRule()).severity(a.getSeverity()).status(a.getStatus())
                .subject(a.getSubject()).message(a.getMessage()).eventCount(a.getEventCount())
                .firstSeenAt(a.getFirstSeenAt()).lastSeenAt(a.getLastSeenAt())
                .acknowledgedBy(a.getAcknowledgedBy()).acknowledgedAt(a.getAcknowledgedAt())
                .acknowledgeNote(a.getAcknowledgeNote())
                .relatedUserId(related == null ? null : related.getId())
                .relatedUserEmail(related == null ? null : related.getEmail())
                .relatedUserStatus(related == null || related.getStatus() == null ? null : related.getStatus().name())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
