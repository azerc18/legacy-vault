package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import com.ltld.app.legacyvault.entity.SecurityAlert;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.SecurityAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityAlertServiceImpl implements SecurityAlertService {

    private final SecurityAlertRepository alertRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<SecurityAlertResponse> list(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return alertRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable)
                .map(SecurityAlertServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpen() {
        return alertRepository.countByStatus(SecurityAlertStatus.OPEN);
    }

    @Override
    @Transactional
    public SecurityAlertResponse acknowledge(UUID adminId, UUID alertId) {
        SecurityAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AdminException("Không tìm thấy cảnh báo", HttpStatus.NOT_FOUND));
        if (alert.getStatus() == SecurityAlertStatus.ACKNOWLEDGED) {
            throw new AdminException("Cảnh báo đã được xác nhận trước đó", HttpStatus.CONFLICT);
        }
        alert.setStatus(SecurityAlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(adminId);
        alert.setAcknowledgedAt(Instant.now());
        SecurityAlert saved = alertRepository.saveAndFlush(alert);   // flush để @Version báo xung đột ngay

        auditLogService.log(AuditAction.SECURITY_ALERT_ACKNOWLEDGED, AuditResult.SUCCESS, adminId, null,
                "SecurityAlert", alertId.toString(), alert.getRule() + " " + alert.getSubject());
        return toResponse(saved);
    }

    private static SecurityAlertStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return SecurityAlertStatus.OPEN;
        try {
            return SecurityAlertStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdminException("Trạng thái không hợp lệ (OPEN, ACKNOWLEDGED)", HttpStatus.BAD_REQUEST);
        }
    }

    private static SecurityAlertResponse toResponse(SecurityAlert a) {
        return SecurityAlertResponse.builder()
                .id(a.getId()).rule(a.getRule()).severity(a.getSeverity()).status(a.getStatus())
                .subject(a.getSubject()).message(a.getMessage()).eventCount(a.getEventCount())
                .firstSeenAt(a.getFirstSeenAt()).lastSeenAt(a.getLastSeenAt())
                .acknowledgedBy(a.getAcknowledgedBy()).acknowledgedAt(a.getAcknowledgedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }
}