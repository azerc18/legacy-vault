package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    @Mock private AuditLogService auditLogService;
    @Mock private VaultModificationGuard vaultModificationGuard;

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

    // ===== FR-04: uploadDocument =====

    @Test
    void uploadDocument_Success() throws Exception {
        byte[] encryptedBytes = "EncryptedBytes".getBytes();
        String savedPath = "uploads/abcd_dichuc.pdf";

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(cryptoService.encryptBytes(any(byte[].class))).thenReturn(encryptedBytes);
        when(fileStorageService.storeFile(any(), any(byte[].class))).thenReturn(savedPath);

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
    void uploadDocument_NotOwner_Returns404() throws Exception {
        // Vault thuộc về người khác — trả 404 (không phải 403) để tránh lộ ID
        User fakeOwner = new User();
        fakeOwner.setId(UUID.randomUUID());
        mockVault.setOwner(fakeOwner);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Vault not found"); // 404, không phải "Unauthorized"

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

        // VaultModificationGuard sẽ ném VaultException
        doThrow(new VaultException("Cannot modify vault that is not in active state"))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Cannot modify vault");
    }

    // ===== FR-04: listDocuments =====

    @Test
    void listDocuments_Success_ReturnsList() {
        LegalDocument doc = new LegalDocument();
        doc.setId(UUID.randomUUID());
        doc.setDocumentType(DocumentType.WILL);
        doc.setFileName("dichuc.pdf");

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByVaultIdOrderByUploadedAtDescIdDesc(vaultId)).thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.listDocuments(ownerId, vaultId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFileName()).isEqualTo("dichuc.pdf");
        assertThat(result.get(0).getDocumentType()).isEqualTo(DocumentType.WILL);
    }

    @Test
    void listDocuments_NotOwner_Returns404() {
        User fakeOwner = new User();
        fakeOwner.setId(UUID.randomUUID());
        mockVault.setOwner(fakeOwner);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> documentService.listDocuments(ownerId, vaultId))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Vault not found");
    }

    // ===== FR-04: deleteDocument =====

    @Test
    void deleteDocument_Success_FileDeletedAfterCommit() {
        UUID documentId = UUID.randomUUID();
        LegalDocument doc = new LegalDocument();
        doc.setId(documentId);
        doc.setFileUrlEncrypted("uploads/test.pdf");
        doc.setVault(mockVault);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByIdAndVaultId(documentId, vaultId)).thenReturn(Optional.of(doc));

        // Bật synchronization để registerSynchronization không ném IllegalStateException
        TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.deleteDocument(ownerId, vaultId, documentId);

            // Chưa xóa file — DB chưa "commit" ở bước này
            verify(fileStorageService, never()).deleteFile(any());

            // Kích hoạt afterCommit callbacks thủ công để xác minh file bị xóa SAU KHI DB commit
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            // Bây giờ file mới bị xóa
            verify(fileStorageService).deleteFile("uploads/test.pdf");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(legalDocumentRepository).delete(doc);
    }

    @Test
    void deleteDocument_NotOwner_Returns404() {
        User fakeOwner = new User();
        fakeOwner.setId(UUID.randomUUID());
        mockVault.setOwner(fakeOwner);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> documentService.deleteDocument(ownerId, vaultId, UUID.randomUUID()))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Vault not found");

        verify(legalDocumentRepository, never()).delete(any());
    }

    @Test
    void deleteDocument_DocumentNotFound_Returns404() {
        UUID documentId = UUID.randomUUID();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByIdAndVaultId(documentId, vaultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(ownerId, vaultId, documentId))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Document not found");

        verify(legalDocumentRepository, never()).delete(any());
    }

    @Test
    void deleteDocument_FileDeleteFails_DoesNotThrow() {
        // M2a: Lỗi xóa file trong afterCommit KHÔNG được làm request trả 500
        UUID documentId = UUID.randomUUID();
        LegalDocument doc = new LegalDocument();
        doc.setId(documentId);
        doc.setFileUrlEncrypted("uploads/gone.pdf");
        doc.setVault(mockVault);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByIdAndVaultId(documentId, vaultId)).thenReturn(Optional.of(doc));
        doThrow(new RuntimeException("disk full")).when(fileStorageService).deleteFile(any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.deleteDocument(ownerId, vaultId, documentId);

            // afterCommit phải không ném exception ra ngoài (try/catch bên trong)
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                    TransactionSynchronizationManager.getSynchronizations()
                            .forEach(TransactionSynchronization::afterCommit)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ===== FR-18: downloadDocumentForOwner =====

    @Test
    void downloadDocumentForOwner_Success() throws Exception {
        UUID documentId = UUID.randomUUID();
        LegalDocument doc = new LegalDocument();
        doc.setId(documentId);
        doc.setFileName("dichuc.pdf");
        doc.setFileUrlEncrypted("uploads/enc_dichuc.pdf");

        byte[] encryptedBytes = "EncryptedData".getBytes();
        byte[] decryptedBytes = "DecryptedPdfContent".getBytes();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByIdAndVaultId(documentId, vaultId)).thenReturn(Optional.of(doc));
        when(fileStorageService.readFile("uploads/enc_dichuc.pdf")).thenReturn(encryptedBytes);
        when(cryptoService.decryptBytes(encryptedBytes)).thenReturn(decryptedBytes);

        DocumentDownloadResponse result = documentService.downloadDocumentForOwner(ownerId, vaultId, documentId);

        assertThat(result.getFileName()).isEqualTo("dichuc.pdf");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getContent()).isEqualTo(decryptedBytes);

        verify(cryptoService).decryptBytes(encryptedBytes);
        // Nội dung đã giải mã không được lưu lại disk — chỉ trong RAM
        verify(fileStorageService, never()).storeFile(any(), any());
    }

    @Test
    void downloadDocumentForOwner_NotOwner_Returns404() {
        User fakeOwner = new User();
        fakeOwner.setId(UUID.randomUUID());
        mockVault.setOwner(fakeOwner);

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));

        assertThatThrownBy(() -> documentService.downloadDocumentForOwner(ownerId, vaultId, UUID.randomUUID()))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Vault not found");
    }
}
