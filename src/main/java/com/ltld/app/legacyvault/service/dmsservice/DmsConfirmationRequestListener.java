package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService.ConfirmationMails;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService.ConfirmerMail;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * X2 (M3): gửi mail xác nhận chéo SAU khi transaction của trigger đã commit (không giữ khoá DmsConfig khi chờ SMTP,
 * rollback thì không gửi). Mỗi confirmer một link tới trang FE /dms/confirm?token=... (C1).
 *
 * <p>Không gửi được mail cho confirmer nào (SMTP lỗi, thiếu app.frontend.base-url...) thì không chờ 72h vô ích
 * mà kích hoạt DMS như luồng cũ, có audit DMS_CONFIRMATION_UNDELIVERED. Gửi được ít nhất một mail thì báo Owner (m5).
 * Lưu ý: nếu ứng dụng dừng giữa lúc commit và lúc gửi, đợt vẫn PENDING_CONFIRMATION và job hết hạn 72h sẽ kích hoạt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmsConfirmationRequestListener {

    private final DmsConfirmerService dmsConfirmerService;
    private final DmsActivationProcessor dmsActivationProcessor;
    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmationRequested(DmsConfirmationRequestedEvent event) {
        ConfirmationMails mails = dmsConfirmerService.prepareConfirmationMails(event.configId(), event.deadlineAt());
        if (mails == null) {
            return;   // Owner vừa check-in / lưu cấu hình, hoặc đợt khác đã bắt đầu
        }

        List<String> notifiedNames = new ArrayList<>();
        for (ConfirmerMail mail : mails.confirmers()) {
            try {
                emailSender.sendDmsCrossConfirmationRequest(mail.email(), mail.name(),
                        mails.ownerName(), mails.ownerEmail(), mails.deadlineAt(), mail.token());
                notifiedNames.add(mail.name());
            } catch (RuntimeException ex) {
                log.warn("X2: failed to send cross-confirmation email to {} for dmsConfig {}: {}",
                        mail.email(), event.configId(), ex.getMessage());
            }
        }

        if (notifiedNames.isEmpty()) {
            log.warn("X2: no trusted confirmer could be emailed for dmsConfig {}; triggering as usual", event.configId());
            dmsActivationProcessor.activateBecauseConfirmersUnreached(event.configId(), event.deadlineAt(),
                    LocalDateTime.now());
            return;
        }

        try {
            emailSender.sendOwnerConfirmationPendingNotice(mails.ownerEmail(), mails.ownerName(), notifiedNames,
                    mails.deadlineAt());
        } catch (RuntimeException ex) {
            log.warn("X2: could not notify owner of pending confirmation for dmsConfig {}: {}",
                    event.configId(), ex.getMessage());
        }
    }
}
