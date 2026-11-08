package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface SecurityAlertService {
    // B3: severity = HIGH | MEDIUM | null (mọi mức)
    Page<SecurityAlertResponse> list(String status, String severity, int page, int size);

    long countOpen();

    // B3: note = ghi chú tuỳ chọn của Admin (tối đa 500 ký tự)
    SecurityAlertResponse acknowledge(UUID adminId, UUID alertId, String note);
}
