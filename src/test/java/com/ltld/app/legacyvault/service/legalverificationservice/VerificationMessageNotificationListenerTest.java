package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.event.VerificationMessagePostedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationMessageNotificationListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailSender emailSender;
    @InjectMocks private VerificationRequestNotificationListener listener;

    private final UUID requestId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID executorId = UUID.randomUUID();

    private User user(UUID id, String email, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setStatus(status);
        return u;
    }

    private VerificationMessagePostedEvent event(UUID verifierId) {
        return new VerificationMessagePostedEvent(requestId, ownerId, executorId, verifierId);
    }

    @Test
    @DisplayName("Assigned verifier gets the message mail, and nobody else is looked up")
    void assignedVerifier_isTheOnlyRecipient() {
        UUID verifierId = UUID.randomUUID();
        when(userRepository.findById(verifierId))
                .thenReturn(Optional.of(user(verifierId, "v@mail.com", UserStatus.ACTIVE)));

        listener.onMessagePosted(event(verifierId));

        verify(emailSender).sendVerificationMessageNotification("v@mail.com", requestId);
        verify(userRepository, never()).findActiveByRoleCode(any());
        verify(emailSender, never()).sendVerificationRequestNotification(any(), any());
    }

    @Test
    @DisplayName("No verifier assigned yet -> every active legal verifier gets the message mail")
    void noAssignedVerifier_notifiesAllVerifiers() {
        User v1 = user(UUID.randomUUID(), "v1@mail.com", UserStatus.ACTIVE);
        User v2 = user(UUID.randomUUID(), "v2@mail.com", UserStatus.ACTIVE);
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(v1, v2));

        listener.onMessagePosted(event(null));

        verify(emailSender).sendVerificationMessageNotification("v1@mail.com", requestId);
        verify(emailSender).sendVerificationMessageNotification("v2@mail.com", requestId);
    }

    @Test
    @DisplayName("An inactive assigned verifier is not mailed")
    void inactiveAssignedVerifier_isSkipped() {
        UUID verifierId = UUID.randomUUID();
        when(userRepository.findById(verifierId))
                .thenReturn(Optional.of(user(verifierId, "v@mail.com", UserStatus.LOCKED)));

        listener.onMessagePosted(event(verifierId));

        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Owner and executor are never mailed even if they are the verifier or hold the role")
    void ownerAndExecutor_areNeverMailed() {
        User asOwner = user(ownerId, "owner@mail.com", UserStatus.ACTIVE);
        User asExecutor = user(executorId, "executor@mail.com", UserStatus.ACTIVE);
        User other = user(UUID.randomUUID(), "other@mail.com", UserStatus.ACTIVE);
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(asOwner, asExecutor, other));

        listener.onMessagePosted(event(null));

        verify(emailSender, never()).sendVerificationMessageNotification(eq("owner@mail.com"), any());
        verify(emailSender, never()).sendVerificationMessageNotification(eq("executor@mail.com"), any());
        verify(emailSender).sendVerificationMessageNotification("other@mail.com", requestId);
    }

    @Test
    @DisplayName("A failing mail for one verifier does not stop the others and nothing escapes")
    void oneMailFailure_doesNotStopOthers() {
        User v1 = user(UUID.randomUUID(), "v1@mail.com", UserStatus.ACTIVE);
        User v2 = user(UUID.randomUUID(), "v2@mail.com", UserStatus.ACTIVE);
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(v1, v2));
        doThrow(new MailSendException("smtp down"))
                .when(emailSender).sendVerificationMessageNotification(eq("v1@mail.com"), any());

        assertDoesNotThrow(() -> listener.onMessagePosted(event(null)));

        verify(emailSender).sendVerificationMessageNotification("v2@mail.com", requestId);
    }

    @Test
    @DisplayName("Recipient lookup failure is swallowed so the saved message is unaffected")
    void lookupFailure_isSwallowed() {
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> listener.onMessagePosted(event(null)));

        verifyNoInteractions(emailSender);
    }
}
