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

        // 2. Phân quyền: Chỉ chủ vault mới xem được
        if (!vault.getOwner().getId().equals(requesterId)) {
            throw new VaultException("Unauthorized: You don't have permission to view logs for this vault", HttpStatus.FORBIDDEN);
        }

        // N6: Default dates and validation
        Instant to = endDate != null ? endDate : Instant.now();
        Instant from = startDate != null ? startDate : to.minus(30, java.time.temporal.ChronoUnit.DAYS);
        if (from.isAfter(to)) throw new VaultException("Ngày bắt đầu phải trước ngày kết thúc.", HttpStatus.BAD_REQUEST);
        if (java.time.Duration.between(from, to).toDays() > 365) throw new VaultException("Khoảng thời gian tối đa 1 năm.", HttpStatus.BAD_REQUEST);

        // 3. Truy vấn logs từ AuditLog (không cần bảng thứ hai)
        Page<AuditLog> logs = auditLogRepository.findVaultActivity(vaultId, from, to, pageable);

        // 4. Map sang Response & Masking Identity (dùng chung với nhật ký cấp Owner)
        return logs.map(log -> descriptionBuilder.toResponse(log, vault.getOwner().getId()));
    }
}
