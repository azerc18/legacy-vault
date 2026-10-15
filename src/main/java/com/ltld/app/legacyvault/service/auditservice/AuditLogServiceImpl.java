package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    private final ApplicationEventPublisher publisher;

    @Override
    public void log(AuditAction action, AuditResult result, UUID userId, String email, UUID vaultId,
                    String targetType, String targetId, String detail) {
        HttpServletRequest req = currentRequest();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result.name());
        if (email != null)  meta.put("email", email);
        if (detail != null) meta.put("detail", detail);
        if (req != null)    meta.put("userAgent", truncate(req.getHeader(HttpHeaders.USER_AGENT), 255));

        publisher.publishEvent(new AuditEvent(
                action, userId, vaultId, targetType,
                targetId == null ? null : UUID.fromString(targetId),
                req != null ? req.getRemoteAddr() : null,
                meta));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s
                ? s.getRequest() : null;
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
