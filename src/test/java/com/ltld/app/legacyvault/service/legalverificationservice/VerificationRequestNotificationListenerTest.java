package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.event.VerificationRequestSubmittedEvent;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Collections;
import java.util.List;
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
class VerificationRequestNotificationListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailSender emailSender;
    @Mock private VaultBeneficiaryRepository beneficiaryRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @InjectMocks private VerificationRequestNotificationListener listener;

    private final UUID requestId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID executorId = UUID.randomUUID();
    private final VerificationRequestSubmittedEvent event =
            new VerificationRequestSubmittedEvent(requestId, ownerId, executorId);

    private User user(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    @Test
    @DisplayName("Every active legal verifier gets a mail containing only the request id")
    void notifiesAllVerifiers() {
        User v1 = user(UUID.randomUUID(), "v1@mail.com");
        User v2 = user(UUID.randomUUID(), "v2@mail.com");
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(v1, v2));

        listener.onSubmitted(event);

        verify(emailSender).sendVerificationRequestNotification("v1@mail.com", requestId);
        verify(emailSender).sendVerificationRequestNotification("v2@mail.com", requestId);
    }

    @Test
    @DisplayName("A failing mail for one verifier does not stop the others and nothing is thrown")
    void oneMailFailureDoesNotStopOthers() {
        User v1 = user(UUID.randomUUID(), "v1@mail.com");
        User v2 = user(UUID.randomUUID(), "v2@mail.com");
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(v1, v2));
        doThrow(new MailSendException("smtp down"))
                .when(emailSender).sendVerificationRequestNotification(eq("v1@mail.com"), any());

        assertDoesNotThrow(() -> listener.onSubmitted(event));

        verify(emailSender).sendVerificationRequestNotification("v2@mail.com", requestId);
    }

    @Test
    @DisplayName("Owner and executor of the request are skipped even if they hold the verifier role")
    void skipsOwnerAndExecutor() {
        User asOwner = user(ownerId, "owner@mail.com");
        User asExecutor = user(executorId, "executor@mail.com");
        User other = user(UUID.randomUUID(), "other@mail.com");
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(asOwner, asExecutor, other));

        listener.onSubmitted(event);

        verify(emailSender, never()).sendVerificationRequestNotification(eq("owner@mail.com"), any());
        verify(emailSender, never()).sendVerificationRequestNotification(eq("executor@mail.com"), any());
        verify(emailSender).sendVerificationRequestNotification("other@mail.com", requestId);
    }

    @Test
    @DisplayName("N2-verifier: verifier who is a beneficiary or another executor of the owner is not notified")
    void skipsConflictingVerifiers() {
        User beneficiary = user(UUID.randomUUID(), "ben@mail.com");
        User otherExecutor = user(UUID.randomUUID(), "exec2@mail.com");
        User other = user(UUID.randomUUID(), "other@mail.com");
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(List.of(beneficiary, otherExecutor, other));
        // Mặc định không xung đột; chỉ 2 người dưới đây xung đột (stub cụ thể ghi đè stub mặc định)
        when(beneficiaryRepository.isBeneficiaryOfRequest(eq(requestId), any())).thenReturn(false);
        when(executorAssignmentRepository.existsByOwnerIdAndExecutorUserIdAndStatusNot(
                eq(ownerId), any(), eq(ExecutorStatus.REVOKED))).thenReturn(false);
        when(beneficiaryRepository.isBeneficiaryOfRequest(requestId, beneficiary.getId())).thenReturn(true);
        when(executorAssignmentRepository.existsByOwnerIdAndExecutorUserIdAndStatusNot(
                ownerId, otherExecutor.getId(), ExecutorStatus.REVOKED)).thenReturn(true);

        listener.onSubmitted(event);

        verify(emailSender, never()).sendVerificationRequestNotification(eq("ben@mail.com"), any());
        verify(emailSender, never()).sendVerificationRequestNotification(eq("exec2@mail.com"), any());
        verify(emailSender).sendVerificationRequestNotification("other@mail.com", requestId);
    }

    @Test
    @DisplayName("No verifiers -> nothing is sent")
    void noVerifiers() {
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenReturn(Collections.emptyList());

        listener.onSubmitted(event);

        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("Verifier lookup failure is swallowed so the committed request is unaffected")
    void lookupFailureIsSwallowed() {
        when(userRepository.findActiveByRoleCode("LEGAL_VERIFIER")).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> listener.onSubmitted(event));

        verifyNoInteractions(emailSender);
    }
}
