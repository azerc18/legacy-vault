package com.ltld.app.legacyvault.controller.user;

import com.ltld.app.legacyvault.controller.UserController;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.service.userservice.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final String BASE = "/api/users/me";
    private static final String CHANGE_PW = BASE + "/change-password";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserProfileService userProfileService;

    private final UUID userId = UUID.randomUUID();
    private final Principal principal = () -> userId.toString();

    private UserProfileResponse sample(String fullName, String phone) {
        return UserProfileResponse.builder()
                .id(userId)
                .email("owner@example.com")
                .fullName(fullName)
                .phoneNumber(phone)
                .status(UserStatus.ACTIVE)
                .roles(List.of("BENEFICIARY", "OWNER"))
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .lastLoginAt(Instant.parse("2026-10-01T09:00:00Z"))
                .build();
    }

    // ---------- GET /api/users/me ----------

    @Test
    void getMe_returns200WithProfileAndNoStoreCache() throws Exception {
        when(userProfileService.getMe(userId)).thenReturn(sample("Nguyen Van A", "0901234567"));

        mockMvc.perform(get(BASE).principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("owner@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles[0]").value("BENEFICIARY"))
                .andExpect(jsonPath("$.data.roles[1]").value("OWNER"));
    }

    @Test
    void getMe_userNotFound_returns404() throws Exception {
        when(userProfileService.getMe(userId))
                .thenThrow(new UserProfileException("Account not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get(BASE).principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    // ---------- PUT /api/users/me ----------

    @Test
    void updateMe_validBody_returns200AndPassesRequestToService() throws Exception {
        when(userProfileService.updateMe(eq(userId), any())).thenReturn(sample("Tran Thi B", "0912345678"));

        mockMvc.perform(put(BASE).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tran Thi B\",\"phoneNumber\":\"0912345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully"))
                .andExpect(jsonPath("$.data.fullName").value("Tran Thi B"));

        verify(userProfileService).updateMe(eq(userId), argThat(r ->
                "Tran Thi B".equals(r.getFullName()) && "0912345678".equals(r.getPhoneNumber())));
    }

    @Test
    void updateMe_emptyPhone_isAccepted() throws Exception {
        when(userProfileService.updateMe(eq(userId), any())).thenReturn(sample("Tran Thi B", null));

        mockMvc.perform(put(BASE).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tran Thi B\",\"phoneNumber\":\"\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateMe_blankName_returns400AndSkipsService() throws Exception {
        mockMvc.perform(put(BASE).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.fullName").exists());

        verify(userProfileService, never()).updateMe(any(), any());
    }

    @Test
    void updateMe_nameTooShort_returns400() throws Exception {
        mockMvc.perform(put(BASE).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fullName").value("Full name must be 2-100 characters"));
    }

    @Test
    void updateMe_invalidPhone_returns400() throws Exception {
        mockMvc.perform(put(BASE).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tran Thi B\",\"phoneNumber\":\"abc123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.phoneNumber").value("Invalid phone number"));

        verify(userProfileService, never()).updateMe(any(), any());
    }

    // ---------- POST /api/users/me/change-password ----------

    @Test
    void changePassword_valid_returns200WithReloginMessage() throws Exception {
        mockMvc.perform(post(CHANGE_PW).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"new-pass\",\"passwordConfirm\":\"new-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully. Please sign in again."));

        verify(userProfileService).changePassword(eq(userId), argThat(r ->
                "old-pass".equals(r.getCurrentPassword()) && "new-pass".equals(r.getNewPassword())));
    }

    @Test
    void changePassword_confirmMismatch_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post(CHANGE_PW).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"new-pass\",\"passwordConfirm\":\"other-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.passwordMatching").value("Password confirmation does not match"));

        verify(userProfileService, never()).changePassword(any(), any());
    }

    @Test
    void changePassword_newTooShort_returns400() throws Exception {
        mockMvc.perform(post(CHANGE_PW).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-pass\",\"newPassword\":\"123\",\"passwordConfirm\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").exists());

        verify(userProfileService, never()).changePassword(any(), any());
    }

    @Test
    void changePassword_missingFields_returns400() throws Exception {
        mockMvc.perform(post(CHANGE_PW).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.currentPassword").exists())
                .andExpect(jsonPath("$.data.newPassword").exists())
                .andExpect(jsonPath("$.data.passwordConfirm").exists());
    }

    @Test
    void changePassword_wrongCurrent_returns400WithServiceMessage() throws Exception {
        doThrow(new UserProfileException("Current password is incorrect", HttpStatus.BAD_REQUEST))
                .when(userProfileService).changePassword(eq(userId), any());

        mockMvc.perform(post(CHANGE_PW).principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-pass\",\"passwordConfirm\":\"new-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }
}
