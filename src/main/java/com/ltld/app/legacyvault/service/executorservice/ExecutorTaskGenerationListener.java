package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * F5: Vault mở khóa thì sinh checklist cho tài khoản số EXECUTOR_DISPOSITION.
 * AFTER_COMMIT + @Async như các listener VaultsUnlockedEvent khác; lỗi chỉ log, vì Executor mở checklist sẽ sinh lại.
 */
@Slf4j
@Component
public class ExecutorTaskGenerationListener {

    private final ExecutorTaskService executorTaskService;
    private final TransactionTemplate writeTx;

    public ExecutorTaskGenerationListener(ExecutorTaskService executorTaskService,
                                          PlatformTransactionManager transactionManager) {
        this.executorTaskService = executorTaskService;
        this.writeTx = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW: nếu listener chạy đồng bộ (không @Async) thì không nhập vào transaction vừa commit.
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        try {
            Integer created = writeTx.execute(status -> executorTaskService.generateForVaults(event.vaultIds()));
            log.info("F5: created {} executor task(s) for request {}", created, event.requestId());
        } catch (RuntimeException e) {
            log.warn("F5: generating executor tasks for request {} failed: {}", event.requestId(), e.getMessage());
        }
    }
}
