package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface SecurityAlertService {
    Page<SecurityAlertResponse> list(String status, int page, int size);

    long countOpen();

    SecurityAlertResponse acknowledge(UUID adminId, UUID alertId);
}