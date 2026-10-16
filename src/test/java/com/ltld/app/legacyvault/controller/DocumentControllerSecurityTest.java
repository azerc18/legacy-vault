package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.documentservice.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    // --- POST /api/documents/upload ---

    @Test
    void upload_noToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("vaultId", UUID.randomUUID().toString())
                        .param("type", "WILL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_wrongAuthority_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("vaultId", UUID.randomUUID().toString())
                        .param("type", "WILL")
                        .with(token("VAULT_READ"))) // VAULT_READ không đủ quyền upload
                .andExpect(status().isForbidden());
        verify(documentService, never()).uploadDocument(any(), any(), any(), any());
    }

    @Test
    void upload_withVaultCreate_returns200() throws Exception {
        com.ltld.app.legacyvault.entity.Vault vault = new com.ltld.app.legacyvault.entity.Vault();
        vault.setId(UUID.randomUUID());
        com.ltld.app.legacyvault.entity.LegalDocument mockDoc = new com.ltld.app.legacyvault.entity.LegalDocument();
        mockDoc.setId(UUID.randomUUID());
        mockDoc.setVault(vault);
        mockDoc.setDocumentType(com.ltld.app.legacyvault.enums.DocumentType.WILL);
        mockDoc.setFileName("test.pdf");
        when(documentService.uploadDocument(any(), any(), any(), any()))
                .thenReturn(mockDoc);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("vaultId", UUID.randomUUID().toString())
                        .param("type", "WILL")
                        .with(token("VAULT_CREATE")))
                .andExpect(status().isOk());
    }

    // --- GET /api/documents/{vaultId} ---

    @Test
    void listDocuments_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/documents/{vaultId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listDocuments_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/documents/{vaultId}", UUID.randomUUID())
                        .with(token("VAULT_CREATE"))) // VAULT_CREATE không đủ quyền read
                .andExpect(status().isForbidden());
    }

    @Test
    void listDocuments_withVaultRead_returns200() throws Exception {
        when(documentService.listDocuments(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/documents/{vaultId}", UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isOk());
    }

    // --- DELETE /api/documents/{vaultId}/{documentId} ---

    @Test
    void deleteDocument_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/documents/{vaultId}/{documentId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteDocument_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(delete("/api/documents/{vaultId}/{documentId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(token("VAULT_READ"))) // VAULT_READ không đủ quyền xóa
                .andExpect(status().isForbidden());
        verify(documentService, never()).deleteDocument(any(), any(), any());
    }

    @Test
    void deleteDocument_withVaultDelete_returns200() throws Exception {
        mockMvc.perform(delete("/api/documents/{vaultId}/{documentId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(token("VAULT_DELETE")))
                .andExpect(status().isOk());
    }

    // --- GET /api/documents/{vaultId}/{documentId}/download ---

    @Test
    void downloadDocument_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/documents/{vaultId}/{documentId}/download",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadDocument_withVaultRead_returns200() throws Exception {
        when(documentService.downloadDocumentForOwner(any(), any(), any()))
                .thenReturn(com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse.builder()
                        .fileName("test.pdf")
                        .contentType("application/pdf")
                        .content("data".getBytes())
                        .build());

        mockMvc.perform(get("/api/documents/{vaultId}/{documentId}/download",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assert contentType != null && contentType.startsWith("application/pdf");
                });
    }
}
