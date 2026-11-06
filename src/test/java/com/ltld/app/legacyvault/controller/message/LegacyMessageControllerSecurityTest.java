package com.ltld.app.legacyvault.controller.message;

import com.ltld.app.legacyvault.controller.LegacyMessageController;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.dto.messagedto.UpdateLegacyMessageRequest;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.messageservice.LegacyMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sửa thư di ngôn: role OWNER mặc định (DataInitializer) chỉ có VAULT_CREATE/READ/DELETE/EXECUTOR_MANAGE,
// không có VAULT_UPDATE -> trước đây Owner bấm "Sửa" trên FE luôn bị 403.
@WebMvcTest(LegacyMessageController.class)
@Import(SecurityConfig.class)
class LegacyMessageControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LegacyMessageService legacyMessageService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static MockMultipartHttpServletRequestBuilder updateRequest(UUID vaultId, UUID messageId) {
        return multipart(HttpMethod.PUT, "/api/vaults/{vaultId}/messages/{messageId}", vaultId, messageId)
                .param("title", "Edited letter")
                .param("content", "New content")
                .param("recipientType", "ALL_BENEFICIARIES")
                .param("deliveryTrigger", "ON_CLAIM");
    }

    @Test
    void updateMessage_ownerDefaultAuthorities_returns200() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(legacyMessageService.updateMessage(eq(ownerId), eq(vaultId), eq(messageId), any(UpdateLegacyMessageRequest.class), any()))
                .thenReturn(LegacyMessageResponse.builder().id(messageId).vaultId(vaultId).title("Edited letter").build());

        mockMvc.perform(updateRequest(vaultId, messageId)
                        .with(token(ownerId, "ROLE_OWNER", "VAULT_CREATE", "VAULT_READ", "VAULT_DELETE", "EXECUTOR_MANAGE")))
                .andExpect(status().isOk());

        verify(legacyMessageService).updateMessage(eq(ownerId), eq(vaultId), eq(messageId), any(UpdateLegacyMessageRequest.class), any());
    }

    @Test
    void updateMessage_beneficiaryOnly_returns403() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(updateRequest(UUID.randomUUID(), UUID.randomUUID())
                        .with(token(userId, "ROLE_BENEFICIARY", "VAULT_READ", "ASSET_DOWNLOAD")))
                .andExpect(status().isForbidden());

        verify(legacyMessageService, never()).updateMessage(any(), any(), any(), any(), any());
    }
}
