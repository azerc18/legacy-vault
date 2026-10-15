package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.event.VerificationRequestSubmittedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationRequestNotificationListener {

    static final String VERIFIER_ROLE_CODE = "LEGAL_VERIFIER";

    private final UserRepository userRepository;
    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(VerificationRequestSubmittedEvent event) {
        List<User> verifiers;
        try {
            verifiers = userRepository.findActiveByRoleCode(VERIFIER_ROLE_CODE);
        } catch (RuntimeException e) {
            log.warn("Cannot load legal verifiers to notify for request {}", event.requestId(), e);
            return;
        }

        for (User verifier : verifiers) {
            if (verifier.getId().equals(event.ownerId()) || verifier.getId().equals(event.executorId())) {
                continue;
            }
            try {
                emailSender.sendVerificationRequestNotification(verifier.getEmail(), event.requestId());
            } catch (RuntimeException e) {
                log.warn("Notify verifier {} failed for request {}", verifier.getId(), event.requestId(), e);
            }
        }
    }
}
