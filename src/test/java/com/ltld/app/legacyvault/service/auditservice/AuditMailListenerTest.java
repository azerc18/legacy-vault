package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditMailListenerTest {

    @Mock private EmailSender emailSender;
    @Mock private UserRepository userRepository;
    @InjectMocks private AuditMailListener listener;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("an@example.com");
        user.setFullName("An");
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private AuditEvent event(AuditAction action, String result, String detail) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result);
        if (detail != null) {
            meta.put("detail", detail);
        }
        return new AuditEvent(action, userId, null, null, null, null, meta);
    }

    @Test
    void emailVerified_sendsWelcome() {
        listener.handle(event(AuditAction.EMAIL_VERIFIED, "SUCCESS", null));

        verify(emailSender).sendWelcomeEmail("an@example.com", "An");
    }

    @Test
    void passwordResetSuccess_sendsPasswordChangedNotice() {
        listener.handle(event(AuditAction.PASSWORD_RESET_SUCCESS, "SUCCESS", null));

        verify(emailSender).sendPasswordChangedNotice("an@example.com", "An");
    }

    @Test
    void failedResult_sendsNothing() {
        listener.handle(event(AuditAction.PASSWORD_RESET_SUCCESS, "FAILURE", null));

        verifyNoInteractions(emailSender);
    }

    @Test
    void accountLocked_afterFailedAttempts_sendsLockedNoticeWithMinutes() {
        String until = Instant.now().plusSeconds(15 * 60).toString();

        listener.handle(event(AuditAction.ACCOUNT_LOCKED, "FAILURE", "locked until " + until + " after 5 failed attempts"));

        verify(emailSender).sendAccountLockedNotice("an@example.com", "An", 15);
    }

    @Test
    void accountLocked_attemptWhileAlreadyLocked_sendsNothing() {
        listener.handle(event(AuditAction.ACCOUNT_LOCKED, "FAILURE", "account locked"));

        verify(emailSender, never()).sendAccountLockedNotice(any(), any(), anyInt());
    }

    @Test
    void dmsCheckin_sendsConfirmationWithNextDeadline() {
        listener.handle(event(AuditAction.DMS_CHECKIN, "SUCCESS",
                "owner=" + userId + ", nextCheckinDueAt=2026-12-01T10:30"));

        verify(emailSender).sendDmsCheckinConfirmation(eq("an@example.com"), eq("An"),
                eq(LocalDateTime.of(2026, 12, 1, 10, 30)));
    }

    @Test
    void unknownActor_sendsNothing() {
        AuditEvent e = new AuditEvent(AuditAction.EMAIL_VERIFIED, UUID.randomUUID(), null, null, null, null,
                Map.of("result", "SUCCESS"));

        listener.handle(e);

        verifyNoInteractions(emailSender);
    }

    @Test
    void parsersToleratBadInput() {
        assertThat(AuditMailListener.nextCheckin("nextCheckinDueAt=not-a-date")).isNull();
        assertThat(AuditMailListener.nextCheckin(null)).isNull();
        assertThat(AuditMailListener.lockMinutes("locked until garbage")).isEqualTo(15);
    }
}
