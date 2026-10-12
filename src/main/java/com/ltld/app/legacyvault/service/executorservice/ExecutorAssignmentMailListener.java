package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.event.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.event.ExecutorAssignmentChangedEvent.ExecutorMail;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FR-06 bước 5: gửi thông báo thay đổi quyền cho Executor, CHỈ sau khi transaction đã commit.
 * Thay đổi đã được lưu nên lỗi gửi mail chỉ được log, không ném ra (không làm hỏng response của Owner)
 * và không chặn các mail còn lại trong cùng event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorAssignmentMailListener {

    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ExecutorAssignmentChangedEvent event) {
        for (ExecutorMail mail : event.mails()) {
            try {
                switch (mail.type()) {
                    case ASSIGNED -> emailSender.sendExecutorAssignedNotice(
                            mail.recipientEmail(), mail.ownerName(), mail.ownerEmail(), mail.executorType());
                    case REVOKED -> emailSender.sendExecutorRevokedNotice(
                            mail.recipientEmail(), mail.ownerName(), mail.ownerEmail(), mail.executorType());
                }
            } catch (RuntimeException e) {
                log.warn("FR-06: sending {} notice for executor position {} failed: {}",
                        mail.type(), mail.executorType(), e.getMessage());
            }
        }
    }
}
