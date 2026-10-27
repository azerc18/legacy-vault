package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
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
public class OwnerActivityLogServiceImpl implements OwnerActivityLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditDescriptionBuilder descriptionBuilder;

    @Override
    @Transactional(readOnly = true)
    public Page<VaultActivityLogResponse> getOwnerActivityLogs(UUID ownerId, Instant startDate, Instant endDate, Pageable pageable) {
        // Y2 (T-1): không truyền from thì xem từ đầu (EPOCH), không giới hạn độ dài khoảng ngày
        Instant to = endDate != null ? endDate : Instant.now();
        Instant from = startDate != null ? startDate : Instant.EPOCH;
        if (from.isAfter(to)) throw new DmsException("Ngày bắt đầu phải trước ngày kết thúc.", HttpStatus.BAD_REQUEST);

        // Lọc theo entity (DmsConfig / mọi ExecutorAssignment của Owner, kể cả REVOKED) và VAULT_DELETED do Owner xóa;
        // bỏ các action chỉ Admin cần thấy. Owner chưa có gì -> trang rỗng
        Page<AuditLog> logs = auditLogRepository.findOwnerActivity(ownerId,
                DmsActivationProcessor.ENTITY_DMS_CONFIG, DmsActivationProcessor.ENTITY_EXECUTOR_ASSIGNMENT,
                AuditDescriptionBuilder.OWNER_HIDDEN_ACTIONS, from, to, pageable);

        return logs.map(log -> descriptionBuilder.toResponse(log, ownerId));
    }
}
