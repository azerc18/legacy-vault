package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.event.VerifierReplyPostedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerifierReplyNotificationListener {

    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReplyPosted(VerifierReplyPostedEvent event) {
        try {
            userRepository.findById(event.executorId())
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(u -> emailSender.sendVerificationMessageNotification(
                            u.getEmail(), event.requestId()));
        } catch (RuntimeException e) {
            log.warn("Notify executor {} failed for request {}", event.executorId(), event.requestId(), e);
        }
    }
}