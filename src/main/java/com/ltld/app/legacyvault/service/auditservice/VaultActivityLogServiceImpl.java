package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultActivityLogServiceImpl implements VaultActivityLogService {

    private final AuditLogRepository auditLogRepository;
    private final VaultRepository vaultRepository;
    private final AuditDescriptionBuilder descriptionBuilder;

    @Override
    @Transactional(readOnly = true)
    public Page<VaultActivityLogResponse> getVaultActivityLogs(UUID vaultId, UUID requesterId, Instant startDate, Instant endDate, Pageable pageable) {
        // 1. Kiểm tra vault tồn tại
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        // 2. Phân quyền: Chỉ chủ vault mới xem được (chống lộ ID bằng 404)
        if (!vault.getOwner().getId().equals(requesterId)) {
            throw new VaultException("Vault not found", HttpStatus.NOT_FOUND);
        }

        // T-1 / V3: Default dates and validation (mặc định EPOCH, bỏ giới hạn 365 ngày)
        Instant to = endDate != null ? endDate : Instant.now();
        Instant from = startDate != null ? startDate : Instant.EPOCH;
        if (from.isAfter(to)) throw new VaultException("Start date must be before end date.", HttpStatus.BAD_REQUEST);

        // 3. Truy vấn logs từ AuditLog (ẩn các thao tác nội bộ của Admin theo Q42)
        Page<AuditLog> logs = auditLogRepository.findVaultActivity(vaultId, AuditDescriptionBuilder.OWNER_HIDDEN_ACTIONS, from, to, pageable);

        // 4. Map sang Response & Masking Identity (dùng chung với nhật ký cấp Owner)
        return logs.map(log -> descriptionBuilder.toResponse(log, vault.getOwner().getId()));
    }
}
