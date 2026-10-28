package com.ltld.app.legacyvault.controller.document;

import com.ltld.app.legacyvault.controller.DocumentController;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Document uploaded and encrypted securely"));
    }

    @Test
    void uploadDocument_TooLarge_Returns413() throws Exception {
        // Mô phỏng MaxUploadSizeExceededException — thực tế exception này do
        // multipart resolver ném trước khi controller chạy khi file > 10MB.
        // Ở đây ta throw từ service để đảm bảo GlobalExceptionHandler xử lý đúng (M5+M6).
        when(documentService.uploadDocument(any(), any(), any(), any()))
                .thenThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", new byte[11 * 1024 * 1024]
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(largeFile)
                        .param("vaultId", UUID.randomUUID().toString())
                        .param("type", "WILL")
                        .with(csrf())
                        .principal(() -> UUID.randomUUID().toString()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("File exceeds the allowed size (10MB)."));
    }

    @Test
    void downloadDocument_ArchivedVault_Returns410() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        when(documentService.downloadDocumentForOwner(eq(documentId), eq(ownerId)))
                .thenThrow(new com.ltld.app.legacyvault.exception.VaultException(
                        "This vault has been archived; its documents were deleted under the retention policy.",
                        org.springframework.http.HttpStatus.GONE));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/documents/{documentId}/download", documentId)
                        .principal(mockPrincipal))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This vault has been archived; its documents were deleted under the retention policy."));
    }
}
