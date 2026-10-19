package com.ltld.app.legacyvault.controller.document;

import com.ltld.app.legacyvault.controller.DocumentController;
import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.documentservice.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
class DocumentControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DocumentService documentService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    // --- GET /api/documents/vault/{vaultId} ---
    @Test
    void getDocumentsByVault_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/documents/vault/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDocumentsByVault_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/documents/vault/{id}", UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDocumentsByVault_withVaultRead_returns200() throws Exception {
        when(documentService.getDocumentsByVault(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/documents/vault/{id}", UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isOk());
    }

    // --- GET /api/documents/{id}/download ---
    @Test
    void downloadDocument_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/documents/{id}/download", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadDocument_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/documents/{id}/download", UUID.randomUUID())
                        .with(token("OTHER_AUTH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadDocument_withVaultRead_returns200() throws Exception {
        DocumentDownloadResponse mockFile = DocumentDownloadResponse.builder()
                .fileName("test.pdf")
                .contentType("application/pdf")
                .content(new byte[]{1, 2, 3})
                .build();
        when(documentService.downloadDocumentForOwner(any(), any())).thenReturn(mockFile);

        mockMvc.perform(get("/api/documents/{id}/download", UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isOk());
    }

    // --- DELETE /api/documents/{id} ---
    @Test
    void deleteDocument_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteDocument_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDocument_withVaultDelete_returns200() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID())
                        .with(token("VAULT_DELETE")))
                .andExpect(status().isOk());
    }
}
