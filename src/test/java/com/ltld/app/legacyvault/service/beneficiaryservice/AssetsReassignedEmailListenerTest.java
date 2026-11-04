package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.event.AssetsReassignedEvent;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetsReassignedEmailListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailSender emailSender;

    private final UUID vaultId = UUID.randomUUID();

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName("Người nhận");
        return u;
    }

    private AssetsReassignedEvent event(AssetsReassignedEvent.Reason reason, AssetsReassignedEvent.Receiver... receivers) {
        return new AssetsReassignedEvent(vaultId, "Gia đình", "Bình", List.of(receivers), reason);
    }

    @Test
    void sendsOneEmailPerReceiver_withNoResponseFlag() {
        User a = user("a@example.com");
        when(userRepository.findById(a.getId())).thenReturn(Optional.of(a));

        new AssetsReassignedEmailListener(userRepository, emailSender).onAssetsReassigned(
                event(AssetsReassignedEvent.Reason.NO_RESPONSE, new AssetsReassignedEvent.Receiver(a.getId(), 2)));

        verify(emailSender).sendAssetsReassignedNotice("a@example.com", "Người nhận", "Bình", "Gia đình",
                2, true, vaultId);
    }

    @Test
    void declinedReason_sendsWithNoResponseFalse() {
        User a = user("a@example.com");
        when(userRepository.findById(a.getId())).thenReturn(Optional.of(a));

        new AssetsReassignedEmailListener(userRepository, emailSender).onAssetsReassigned(
                event(AssetsReassignedEvent.Reason.DECLINED, new AssetsReassignedEvent.Receiver(a.getId(), 1)));

        verify(emailSender).sendAssetsReassignedNotice(eq("a@example.com"), any(), any(), any(),
                eq(1), eq(false), eq(vaultId));
    }

    @Test
    void unknownUser_isSkipped() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        new AssetsReassignedEmailListener(userRepository, emailSender).onAssetsReassigned(
                event(AssetsReassignedEvent.Reason.NO_RESPONSE, new AssetsReassignedEvent.Receiver(missing, 1)));

        verify(emailSender, never()).sendAssetsReassignedNotice(any(), any(), any(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void oneEmailFailing_doesNotBlockTheOthers() {
        User a = user("a@example.com");
        User b = user("b@example.com");
        when(userRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(userRepository.findById(b.getId())).thenReturn(Optional.of(b));
        doThrow(new RuntimeException("smtp down")).when(emailSender)
                .sendAssetsReassignedNotice(eq("a@example.com"), any(), any(), any(), anyInt(), anyBoolean(), any());

        new AssetsReassignedEmailListener(userRepository, emailSender).onAssetsReassigned(
                event(AssetsReassignedEvent.Reason.NO_RESPONSE,
                        new AssetsReassignedEvent.Receiver(a.getId(), 1),
                        new AssetsReassignedEvent.Receiver(b.getId(), 1)));

        verify(emailSender).sendAssetsReassignedNotice(eq("b@example.com"), any(), any(), any(),
                anyInt(), anyBoolean(), any());
    }
}