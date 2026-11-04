package com.ltld.app.legacyvault.service.beneficiaryservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * N2-notify: quét hằng giờ các vault đã qua cửa sổ phản hồi và chuyển phần của người chưa phản hồi.
 * Dùng chung công tắc với ClaimTimeoutScheduler (app.beneficiary.claim-expiry.enabled=false trong integration test).
 * Mỗi vault một giao dịch; một vault lỗi không chặn vault khác.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.beneficiary.claim-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class AllocationFallbackScheduler {

    private final AllocationTimeoutFallbackService service;

    public AllocationFallbackScheduler(AllocationTimeoutFallbackService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.beneficiary.allocation-fallback-scan-delay:PT1H}")
    public void scan() {
        run(LocalDateTime.now());
    }

    public int run(LocalDateTime now) {
        int total = 0;
        for (UUID vaultId : service.findDueVaultIds(now)) {
            try {
                total += service.applyFallback(vaultId, now);
            } catch (RuntimeException e) {
                log.error("N2-notify: could not apply timeout fallback for vault {}: {}", vaultId, e.getMessage());
            }
        }
        return total;
    }
}