package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FR-06 bước 5: gửi thông báo thay đổi quyền cho Executor, CHỈ sau khi transaction đã commit.
 *
 * <p>Chạy @Async trên executor mặc định của Spring (@EnableAsync ở LegacyVaultApplication), nên request của
 * Owner không phải chờ SMTP. AFTER_COMMIT quyết định CÓ gửi hay không (rollback thì không gửi), @Async chỉ đưa
 * việc gửi sang thread khác. Thay đổi đã được lưu nên lỗi gửi mail chỉ được log warn, không ném ra
 * và không chặn các mail còn lại trong cùng event; không có retry, mail lỗi coi như mất.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorAssignmentMailListener {

    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ExecutorAssignmentChangedEvent event) {
        for (ExecutorMail mail : event.mails()) {
            try {
                switch (mail.type()) {
                    case ASSIGNED -> emailSender.sendExecutorAssignedNotice(
                            mail.recipientEmail(), mail.ownerName(), mail.ownerEmail(), mail.executorType());
                    case REVOKED -> emailSender.sendExecutorRevokedNotice(
                            mail.recipientEmail(), mail.ownerName(), mail.ownerEmail(), mail.executorType());
                    case REVOKED_TIMEOUT -> emailSender.sendExecutorTimeoutRevokedNotice(
                            mail.recipientEmail(), mail.ownerName(), mail.ownerEmail(), mail.waitDays());
                    case REPLACED_BY_ADMIN -> emailSender.sendExecutorReplacedByAdminNotice(
                            mail.recipientEmail(), mail.ownerName(), mail.ownerEmail(), mail.executorType(), mail.waitDays());
                }
            } catch (RuntimeException e) {
                log.warn("FR-06: sending {} notice for executor position {} failed: {}",
                        mail.type(), mail.executorType(), e.getMessage());
            }
        }
    }
}
