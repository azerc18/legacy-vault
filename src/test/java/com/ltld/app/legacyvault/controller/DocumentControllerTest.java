package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.service.documentservice.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
public class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DocumentService documentService;

    @Test
    void uploadDocument_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        MockMultipartFile file = new MockMultipartFile(
                "file", "dichuc.pdf", "application/pdf", "dummy content".getBytes()
        );

        Vault mockVault = new Vault();
        mockVault.setId(vaultId);
        
        LegalDocument mockDoc = new LegalDocument();
        mockDoc.setFileName("dichuc.pdf");
        mockDoc.setVault(mockVault);
        mockDoc.setDocumentType(DocumentType.WILL);

        when(documentService.uploadDocument(eq(ownerId), eq(vaultId), eq(DocumentType.WILL), any())).thenReturn(mockDoc);

        // Chú ý: Với API có dạng File Upload, thay vì dùng post(), Spring Boot dùng multipart()
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("vaultId", vaultId.toString())
                        .param("type", "WILL")
                        .with(csrf()) // Chống giả mạo CSRF
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Document uploaded and encrypted securely"));
    }
}
