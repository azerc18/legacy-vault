package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.event.VerificationMessagePostedEvent;
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
import java.util.UUID;
import java.util.function.Consumer;

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
        notifyVerifiers(event.requestId(), event.ownerId(), event.executorId(), null,
                email -> emailSender.sendVerificationRequestNotification(email, event.requestId()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagePosted(VerificationMessagePostedEvent event) {
        notifyVerifiers(event.requestId(), event.ownerId(), event.executorId(), event.verifierId(),
                email -> emailSender.sendVerificationMessageNotification(email, event.requestId()));
    }

    private void notifyVerifiers(UUID requestId, UUID ownerId, UUID executorId, UUID assignedVerifierId,
                                 Consumer<String> send) {
        List<User> recipients;
        try {
            recipients = assignedVerifierId == null
                    ? userRepository.findActiveByRoleCode(VERIFIER_ROLE_CODE)
                    : userRepository.findById(assignedVerifierId)
                            .filter(u -> u.getStatus() == UserStatus.ACTIVE).stream().toList();
        } catch (RuntimeException e) {
            log.warn("Cannot load legal verifiers to notify for request {}", requestId, e);
            return;
        }

        for (User verifier : recipients) {
            if (verifier.getId().equals(ownerId) || verifier.getId().equals(executorId)) {
                continue;
            }
            try {
                send.accept(verifier.getEmail());
            } catch (RuntimeException e) {
                log.warn("Notify verifier {} failed for request {}", verifier.getId(), requestId, e);
            }
        }
    }
}
