package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

// FR-05: nhật ký cấp Owner cho các sự kiện DMS và Executor (không gắn với Vault nào).
public interface OwnerActivityLogService {
    Page<VaultActivityLogResponse> getOwnerActivityLogs(UUID ownerId, Instant startDate, Instant endDate, Pageable pageable);
}
