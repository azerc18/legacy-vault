package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.event.AssetsReassignedEvent;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetsReassignedNotificationListenerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private AssetsReassignedNotificationListener listener;
    private final UUID vaultId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new AssetsReassignedNotificationListener(notificationRepository, userRepository, transactionManager);
    }

    private User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    @Test
    void savesOneInAppNotificationPerReceiver() {
        User a = user();
        User b = user();
        when(userRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(userRepository.findById(b.getId())).thenReturn(Optional.of(b));

        listener.onAssetsReassigned(new AssetsReassignedEvent(vaultId, "Gia đình", "Bình", List.of(
                new AssetsReassignedEvent.Receiver(a.getId(), 2),
                new AssetsReassignedEvent.Receiver(b.getId(), 1))));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        Notification first = captor.getAllValues().get(0);
        assertThat(first.getRecipient()).isSameAs(a);
        assertThat(first.getType()).isEqualTo(NotificationType.ASSET_REASSIGNED);
        assertThat(first.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(first.getReferenceType()).isEqualTo("Vault");
        assertThat(first.getReferenceId()).isEqualTo(vaultId);
        assertThat(first.getContent()).contains("2 asset(s)", "Gia đình", "Bình");
        assertThat(captor.getAllValues().get(1).getContent()).contains("1 asset(s)");
    }

    @Test
    void oneReceiverFailing_doesNotBlockTheOthers() {
        User a = user();
        User b = user();
        when(userRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(userRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("db down"))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.onAssetsReassigned(new AssetsReassignedEvent(vaultId, "Gia đình", "Bình", List.of(
                new AssetsReassignedEvent.Receiver(a.getId(), 1),
                new AssetsReassignedEvent.Receiver(b.getId(), 1))));

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void unknownUser_isSkipped() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        listener.onAssetsReassigned(new AssetsReassignedEvent(vaultId, "Gia đình", "Bình",
                List.of(new AssetsReassignedEvent.Receiver(missing, 1))));

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}