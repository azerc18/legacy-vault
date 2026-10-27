package com.ltld.app.legacyvault.controller.user;

import com.ltld.app.legacyvault.controller.UserController;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.userservice.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    private static final String BASE = "/api/users/me";
    private static final String UPDATE_BODY = "{\"fullName\":\"Nguyen Van A\",\"phoneNumber\":\"0901234567\"}";
    private static final String CHANGE_BODY =
            "{\"currentPassword\":\"old-pass\",\"newPassword\":\"New-Pass1\",\"passwordConfirm\":\"New-Pass1\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserProfileService userProfileService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static UserProfileResponse profile() {
        return UserProfileResponse.builder().roles(List.of()).build();
    }

    // ---------- Không có token -> 401 ----------

    @Test
    void getMe_noToken_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verify(userProfileService, never()).getMe(any());
    }

    @Test
    void updateMe_noToken_returns401() throws Exception {
        mockMvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).updateMe(any(), any());
    }

    @Test
    void changePassword_noToken_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/change-password").contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isUnauthorized());
        verify(userProfileService, never()).changePassword(any(), any());
    }

    // ---------- Mọi role đã đăng nhập đều dùng được /me ----------

    @Test
    void getMe_ownerToken_returns200_andUsesJwtSubjectAsUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getMe(userId)).thenReturn(profile());

        mockMvc.perform(get(BASE).with(token(userId, "ROLE_OWNER")))
                .andExpect(status().isOk());

        verify(userProfileService).getMe(userId);
    }

    @Test
    void getMe_beneficiaryOnly_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getMe(userId)).thenReturn(profile());

        mockMvc.perform(get(BASE).with(token(userId, "ROLE_BENEFICIARY")))
                .andExpect(status().isOk());
    }

    @Test
    void getMe_verifierOnly_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.getMe(userId)).thenReturn(profile());

        mockMvc.perform(get(BASE).with(token(userId, "ROLE_LEGAL_VERIFIER")))
                .andExpect(status().isOk());
    }

    @Test
    void updateMe_executorToken_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userProfileService.updateMe(eq(userId), any())).thenReturn(profile());

        mockMvc.perform(put(BASE).with(token(userId, "ROLE_EXECUTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());

        verify(userProfileService).updateMe(eq(userId), any());
    }

    @Test
    void changePassword_adminToken_returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/change-password").with(token(userId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE_BODY))
                .andExpect(status().isOk());

        verify(userProfileService).changePassword(eq(userId), any());
    }
}
