package com.ltld.app.legacyvault.controller.notification;

import com.ltld.app.legacyvault.controller.NotificationController;
import com.ltld.app.legacyvault.dto.notificationdto.NotificationResponse;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InAppNotificationService inAppNotificationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    private static RequestPostProcessor token(String subject) {
        return jwt().jwt(j -> j.subject(subject));
    }

    @Test
    void listMine_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(inAppNotificationService);
    }

    @Test
    void listMine_returns200WithPageOfOwnNotifications() throws Exception {
        when(inAppNotificationService.listMine(userId, 1, 5)).thenReturn(new PageImpl<>(List.of(
                NotificationResponse.builder().id(notificationId).type(NotificationType.VERIFIER_REPLY)
                        .content("The Legal Verifier replied").read(false)
                        .createdAt(LocalDateTime.of(2026, 10, 28, 9, 0)).build())));

        mockMvc.perform(get("/api/notifications/me").param("page", "1").param("size", "5")
                        .with(token(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("VERIFIER_REPLY"))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
    }

    @Test
    void unreadCount_returns200WithNumber() throws Exception {
        when(inAppNotificationService.countUnread(userId)).thenReturn(4L);

        mockMvc.perform(get("/api/notifications/me/unread-count").with(token(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
    }

    @Test
    void unreadCount_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/me/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    void markRead_ownNotification_returns200() throws Exception {
        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId).with(token(userId.toString())))
                .andExpect(status().isOk());
        verify(inAppNotificationService).markRead(notificationId, userId);
    }

    @Test
    void markRead_foreignNotification_returns404Not403() throws Exception {
        doThrow(new BusinessException("Notification not found.", HttpStatus.NOT_FOUND))
                .when(inAppNotificationService).markRead(any(), any());

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId).with(token(userId.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_invalidId_returns400() throws Exception {
        mockMvc.perform(patch("/api/notifications/{id}/read", "not-a-uuid").with(token(userId.toString())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inAppNotificationService);
    }

    @Test
    void nonUuidSubject_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/me").with(token("someone@example.com")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inAppNotificationService);
    }
}
