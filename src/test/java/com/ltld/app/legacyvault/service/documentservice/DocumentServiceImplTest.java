package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;
    @Mock private com.ltld.app.legacyvault.service.auditservice.AuditLogService auditLogService;

    @InjectMocks private DocumentServiceImpl documentService;

    private User mockOwner;
    private Vault mockVault;
    private UUID ownerId;
    private UUID vaultId;
    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();

        mockOwner = new User();
        mockOwner.setId(ownerId);
        mockOwner.setEmail("owner@example.com");

        mockVault = new Vault();
        mockVault.setId(vaultId);
        mockVault.setOwner(mockOwner);
        mockVault.setStatus(VaultStatus.ACTIVE);

        // Giả lập một file tên là dichuc.pdf
        mockFile = new MockMultipartFile(
                "file", "dichuc.pdf", "application/pdf", "Noi dung text".getBytes()
        );
    }

    @Test
    void uploadDocument_Success() throws Exception {
        byte[] encryptedBytes = "EncryptedBytes".getBytes();
        String savedPath = "uploads/abcd_dichuc.pdf";

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(cryptoService.encryptBytes(any(byte[].class))).thenReturn(encryptedBytes);
        when(fileStorageService.storeFile(any(), any(byte[].class))).thenReturn(savedPath);

        // Mẹo: Trả về chính cái object bị nhét vào hàm save, và set ID giả
        when(legalDocumentRepository.save(any(LegalDocument.class))).thenAnswer(i -> {
            LegalDocument doc = i.getArgument(0);
            doc.setId(UUID.randomUUID());
            return doc;
        });

        LegalDocument result = documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile);

        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("dichuc.pdf");
        assertThat(result.getFileUrlEncrypted()).isEqualTo(savedPath);
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.WILL);

        // Đảm bảo file được đem đi mã hóa đúng 1 lần
        verify(cryptoService, times(1)).encryptBytes(any(byte[].class));
    }

    @Test
    void uploadDocument_NotOwner_ThrowsException() throws Exception {
        User fakeOwner = new User();
        fakeOwner.setId(UUID.randomUUID());
        mockVault.setOwner(fakeOwner); // Cố tình đổi chủ

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        // Mock userRepository cho trường hợp attacker tra cứu email của chính mình
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockOwner));

        // Phải văng lỗi VaultException (không còn là RuntimeException chung chung)
        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Unauthorized");

        // Và đương nhiên file không bao giờ được phép đem đi mã hóa hay lưu
        verify(cryptoService, never()).encryptBytes(any());
    }

    @Test
    void uploadDocument_EmptyFile_ThrowsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, emptyFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("File cannot be empty");
    }

    @Test
    void uploadDocument_Exceeds10MB_ThrowsException() {
        byte[] largeBytes = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, largeFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("exceeds 10MB limit");
    }

    @Test
    void uploadDocument_InvalidContentType_ThrowsException() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "script.sh", "application/x-sh", "echo hello".getBytes());

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, invalidFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Invalid file format");
    }

    @Test
    void uploadDocument_VaultNotActive_ThrowsException() {
        mockVault.setStatus(VaultStatus.ARCHIVED_LOCKED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Cannot upload document to vault in ARCHIVED_LOCKED state");
    }

    // ===== FR-04: Document List, Download & Safe Deletion Tests =====

    @Test
    @DisplayName("getDocumentsByVault: Lấy danh sách tài liệu của két thành công")
    void getDocumentsByVault_Success() {
        LegalDocument doc = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .documentType(DocumentType.WILL)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByVaultIdOrderByUploadedAtDescIdDesc(vaultId))
                .thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.getDocumentsByVault(vaultId, ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFileName()).isEqualTo("dichuc_2026.pdf");
        assertThat(result.get(0).getDocumentType()).isEqualTo(DocumentType.WILL);
    }

    @Test
    @DisplayName("getDocumentsByVault: Người khác xem trộm tài liệu -> Chặn 404 Not Found (m1)")
    void getDocumentsByVault_Unauthorized_ThrowsNotFound() {
        UUID attackerId = UUID.randomUUID();
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> documentService.getDocumentsByVault(vaultId, attackerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("downloadDocumentForOwner: Đọc file mã hóa, giải mã đúng và ghi Audit Log thành công")
    void downloadDocumentForOwner_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .documentType(DocumentType.WILL)
                .uploadedAt(LocalDateTime.now())
                .build();

        byte[] encryptedBytes = "encrypted-content".getBytes();
        byte[] plainBytes = "plain-pdf-content".getBytes();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(fileStorageService.readFile("uploads/encrypted_dichuc.bin")).thenReturn(encryptedBytes);
        when(cryptoService.decryptBytes(encryptedBytes)).thenReturn(plainBytes);

        DocumentDownloadResponse response = documentService.downloadDocumentForOwner(docId, ownerId);

        assertThat(response.getFileName()).isEqualTo("dichuc_2026.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContent()).isEqualTo(plainBytes);

        verify(auditLogService).log(eq(AuditAction.DOCUMENT_DOWNLOADED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.com"), eq(vaultId),
                eq("LegalDocument"), eq(docId.toString()), any());
    }

    @Test
    @DisplayName("downloadDocumentForOwner: Người khác tải trộm tài liệu -> Chặn 404 Not Found (m1)")
    void downloadDocumentForOwner_Unauthorized_ThrowsNotFound() {
        UUID docId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.downloadDocumentForOwner(docId, attackerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(fileStorageService);
        verifyNoInteractions(cryptoService);
    }

    @Test
    @DisplayName("deleteDocument: Xóa file vật lý và xóa bản ghi DB khi két ACTIVE")
    void deleteDocument_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        documentService.deleteDocument(docId, ownerId);

        verify(fileStorageService).deleteFile("uploads/encrypted_dichuc.bin");
        verify(legalDocumentRepository).delete(doc);
        verify(auditLogService).log(eq(AuditAction.DOCUMENT_DELETED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.com"), eq(vaultId),
                eq("LegalDocument"), eq(docId.toString()), any());
    }

    @Test
    @DisplayName("deleteDocument: Người khác xóa trộm tài liệu -> Chặn 404 Not Found (m1)")
    void deleteDocument_Unauthorized_ThrowsNotFound() {
        UUID docId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.deleteDocument(docId, attackerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);

        verify(fileStorageService, never()).deleteFile(any());
        verify(legalDocumentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteDocument: Két đã UNLOCKED -> Chặn 400 Bad Request")
    void deleteDocument_NotActive_ThrowsBadRequest() {
        UUID docId = UUID.randomUUID();
        mockVault.setStatus(VaultStatus.UNLOCKED);
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.deleteDocument(docId, ownerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(fileStorageService, never()).deleteFile(any());
        verify(legalDocumentRepository, never()).delete(any());
    }
}
