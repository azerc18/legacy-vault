package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.AdminAuditLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_RANGE_DAYS = 30;
    static final int MAX_RANGE_DAYS = 365;

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> search(String action, String actorEmail, String ip,
                                              Instant from, Instant to, int page, int size) {
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
        if (start.isAfter(end)) {
            throw new AdminException("Ngày bắt đầu phải trước ngày kết thúc", HttpStatus.BAD_REQUEST);
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new AdminException("Khoảng thời gian tối đa 365 ngày", HttpStatus.BAD_REQUEST);
        }

        String emailPattern = isBlank(actorEmail) ? null : "%" + actorEmail.trim().toLowerCase(Locale.ROOT) + "%";
        String ipFilter = isBlank(ip) ? null : ip.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        return auditLogRepository
                .searchForAdmin(parseAction(action), emailPattern, ipFilter, start, end, pageable)
                .map(AdminAuditLogServiceImpl::toResponse);
    }

    @Override
    public List<String> listActions() {
        return Arrays.stream(AuditAction.values()).map(Enum::name).sorted().toList();
    }

    private static AuditAction parseAction(String action) {
        if (isBlank(action)) return null;
        try {
            return AuditAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdminException("Action không hợp lệ: " + action, HttpStatus.BAD_REQUEST);
        }
    }

    private static AdminAuditLogResponse toResponse(AuditLog log) {
        Map<String, Object> meta = log.getMetadata() == null ? Map.of() : log.getMetadata();
        User actor = log.getActor();
        return AdminAuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .result(str(meta.get("result")))
                .actorId(actor == null ? null : actor.getId())
                // Đăng nhập sai email không có actor, email nằm trong metadata
                .actorEmail(actor != null ? actor.getEmail() : str(meta.get("email")))
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .vaultId(log.getVaultId())
                .ipAddress(log.getIpAddress())
                .detail(str(meta.get("detail")))
                .userAgent(str(meta.get("userAgent")))
                .createdAt(log.getCreatedAt())
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}