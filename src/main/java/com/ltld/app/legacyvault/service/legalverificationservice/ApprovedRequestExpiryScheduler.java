package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FR-12: hồ sơ APPROVED nhưng quá app.verifier.approved-expiry-days ngày (mặc định 7) mà chưa ký thì trả về PENDING
 * (bỏ verifier, xóa OTP ký) để verifier khác nhận. Tránh hồ sơ bị kẹt khi verifier đã duyệt bỏ đi.
 * Mặc định BẬT; tắt bằng app.verifier.approved-expiry.enabled=false.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.verifier.approved-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ApprovedRequestExpiryScheduler {

    static final int BATCH_SIZE = 50;
    private static final String ENTITY = "LegalVerificationRequest";

    private final LegalVerificationRequestRepository requestRepository;
    private final DigitalSignatureRepository signatureRepository;
    private final AuditLogService auditLogService;
    private final int expiryDays;
    private final TransactionTemplate requiresNewTx;

    public ApprovedRequestExpiryScheduler(LegalVerificationRequestRepository requestRepository,
                                          DigitalSignatureRepository signatureRepository,
                                          AuditLogService auditLogService,
                                          PlatformTransactionManager transactionManager,
                                          @Value("${app.verifier.approved-expiry-days:7}") int expiryDays) {
        this.requestRepository = requestRepository;
        this.signatureRepository = signatureRepository;
        this.auditLogService = auditLogService;
        this.expiryDays = expiryDays;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Chạy mỗi giờ; trễ 10 phút lần đầu để không chạy ngay lúc khởi động
    @Scheduled(fixedDelayString = "${app.verifier.approved-expiry-scan-delay:PT1H}",
            initialDelayString = "${app.verifier.approved-expiry-initial-delay:PT10M}")
    public void scan() {
        reopenExpired(LocalDateTime.now());
    }

    public void reopenExpired(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(expiryDays);
        List<UUID> ids = requestRepository.findExpiredUnsignedIds(
                LegalVerificationStatus.APPROVED, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (UUID id : ids) {
            try {
                Boolean reopened = requiresNewTx.execute(status -> reopen(id, cutoff, status));
                if (Boolean.TRUE.equals(reopened)) {
                    auditLogService.log(AuditAction.LEGAL_REQUEST_REOPENED, AuditResult.SUCCESS,
                            null, null, ENTITY, id.toString(),
                            "approved over " + expiryDays + " days without signature");
                }
            } catch (RuntimeException e) {
                log.error("FR-12: could not reopen request {}: {}", id, e.getMessage());
            }
        }
    }

    // Khóa hồ sơ rồi kiểm tra lại điều kiện (có thể vừa được ký hoặc từ chối)
    private boolean reopen(UUID id, LocalDateTime cutoff, TransactionStatus status) {
        LegalVerificationRequest r = requestRepository.findByIdForUpdate(id).orElse(null);
        if (r == null || r.getStatus() != LegalVerificationStatus.APPROVED
                || r.getDecidedAt() == null || !r.getDecidedAt().isBefore(cutoff)
                || signatureRepository.existsByRequestId(id)) {
            status.setRollbackOnly();
            return false;
        }
        r.setStatus(LegalVerificationStatus.PENDING);
        r.setVerifier(null);
        r.setDecidedAt(null);
        r.setSignOtpHash(null);
        r.setSignOtpExpiresAt(null);
        r.setSignOtpSentAt(null);
        r.setSignAttempts(0);
        requestRepository.save(r);
        return true;
    }
}
