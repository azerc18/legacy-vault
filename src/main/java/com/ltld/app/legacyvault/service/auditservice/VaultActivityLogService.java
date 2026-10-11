package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface VaultActivityLogService {
    Page<VaultActivityLogResponse> getVaultActivityLogs(UUID vaultId, UUID requesterId, Instant startDate, Instant endDate, Pageable pageable);
}
