package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

// FR-05 & F6: nhật ký cấp Owner cho các sự kiện DMS/Executor và nhật ký minh bạch "Ai đã xem gì".
public interface OwnerActivityLogService {
    Page<VaultActivityLogResponse> getOwnerActivityLogs(UUID ownerId, Instant startDate, Instant endDate, Pageable pageable);

    /** F6: Nhật ký minh bạch "Ai đã xem gì" (Transparency Access Log) trên toàn bộ các Vault của Owner. */
    Page<VaultActivityLogResponse> getEstateAccessLogs(UUID ownerId, Instant startDate, Instant endDate, Pageable pageable);
}
