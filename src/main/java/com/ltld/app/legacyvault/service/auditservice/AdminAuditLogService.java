package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.AdminAuditLogResponse;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

public interface AdminAuditLogService {
    Page<AdminAuditLogResponse> search(String action, String actorEmail, String ip,
                                       Instant from, Instant to, int page, int size);

    List<String> listActions();
}