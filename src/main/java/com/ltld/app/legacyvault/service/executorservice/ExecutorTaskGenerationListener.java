package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * F5: Vault mở khóa thì sinh checklist cho tài khoản số EXECUTOR_DISPOSITION.
 * AFTER_COMMIT + @Async như các listener VaultsUnlockedEvent khác; generateForVaults tự ghi từng task trong transaction
 * riêng. Lỗi chỉ log, vì Executor mở checklist sẽ sinh lại.
 */
@Slf4j
@Component
public class ExecutorTaskGenerationListener {

    private final ExecutorTaskService executorTaskService;

    public ExecutorTaskGenerationListener(ExecutorTaskService executorTaskService) {
        this.executorTaskService = executorTaskService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        try {
            int created = executorTaskService.generateForVaults(event.vaultIds());
            log.info("F5: created {} executor task(s) for request {}", created, event.requestId());
        } catch (RuntimeException e) {
            log.warn("F5: generating executor tasks for request {} failed: {}", event.requestId(), e.getMessage());
        }
    }
}
