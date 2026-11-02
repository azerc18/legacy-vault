package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.dto.notificationdto.NotificationResponse;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private InAppNotificationServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    private Notification notification(NotificationStatus status) {
        return Notification.builder().id(notificationId).type(NotificationType.VERIFICATION_DECIDED)
                .channel(NotificationChannel.IN_APP).referenceType("LegalVerificationRequest")
                .referenceId(UUID.randomUUID()).content("decided").status(status).build();
    }

    @Test
    void listMine_onlyInAppOfCaller_newestFirst_sizeClamped() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(notificationRepository.findByRecipientIdAndChannel(eq(userId), eq(NotificationChannel.IN_APP),
                pageable.capture())).thenReturn(new PageImpl<>(List.of(notification(NotificationStatus.READ))));

        Page<NotificationResponse> page = service.listMine(userId, -3, 1000);

        NotificationResponse n = page.getContent().get(0);
        assertThat(n.isRead()).isTrue();
        assertThat(n.getReferenceType()).isEqualTo("LegalVerificationRequest");
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void listMine_sentNotification_isUnread() {
        when(notificationRepository.findByRecipientIdAndChannel(eq(userId), eq(NotificationChannel.IN_APP), any()))
                .thenReturn(new PageImpl<>(List.of(notification(NotificationStatus.SENT))));

        assertThat(service.listMine(userId, 0, 20).getContent().get(0).isRead()).isFalse();
    }

    @Test
    void countUnread_countsInAppNotRead() {
        when(notificationRepository.countByRecipientIdAndChannelAndStatusNot(
                userId, NotificationChannel.IN_APP, NotificationStatus.READ)).thenReturn(7L);

        assertThat(service.countUnread(userId)).isEqualTo(7);
    }

    @Test
    void markRead_ownUnread_setsRead() {
        Notification n = notification(NotificationStatus.SENT);
        when(notificationRepository.findByIdAndRecipientIdAndChannel(notificationId, userId, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(n));

        service.markRead(notificationId, userId);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
        verify(notificationRepository).save(n);
    }

    @Test
    void markRead_alreadyRead_isIdempotent() {
        when(notificationRepository.findByIdAndRecipientIdAndChannel(notificationId, userId, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(notification(NotificationStatus.READ)));

        service.markRead(notificationId, userId);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_foreignOrMissing_is404() {
        when(notificationRepository.findByIdAndRecipientIdAndChannel(notificationId, userId, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
