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
    @Mock private com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard vaultModificationGuard;
    @Mock private com.ltld.app.legacyvault.service.paymentservice.EntitlementService entitlementService;

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

        // Phải văng lỗi VaultException với mã 404 NOT_FOUND chống dò ID
        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .satisfies(ex -> assertThat(((VaultException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        // Và đương nhiên file không bao giờ được phép đem đi mã hóa hay lưu
        verify(cryptoService, never()).encryptBytes(any());
    }

    @Test
    void downloadDocumentForOwner_VaultArchivedLocked_ThrowsGone() {
        UUID docId = UUID.randomUUID();
        mockVault.setStatus(VaultStatus.ARCHIVED_LOCKED);
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.downloadDocumentForOwner(docId, ownerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.GONE);

        verifyNoInteractions(fileStorageService);
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
    void uploadDocument_VaultNotActive_ThrowsException() throws Exception {
        mockVault.setStatus(VaultStatus.ARCHIVED_LOCKED);
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        doThrow(new VaultException("Cannot modify vault that is not in active state", HttpStatus.BAD_REQUEST))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("Cannot modify vault that is not in active state");
    }

    @Test
    @DisplayName("uploadDocument (m3): DMS đã TRIGGERED -> Guard chặn upload -> CONFLICT 409")
    void uploadDocument_DmsTriggered_ThrowsConflict() throws Exception {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        doThrow(new VaultException("Cannot modify vault: dead-man switch has been triggered", HttpStatus.CONFLICT))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, mockFile))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        verify(cryptoService, never()).encryptBytes(any());
        verify(fileStorageService, never()).storeFile(any(), any());
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
        when(legalDocumentRepository.findByVaultIdAndIsCurrentTrueOrderByUploadedAtDescIdDesc(vaultId))
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
    @DisplayName("deleteDocument (M2b): Chỉ xóa file vật lý SAU KHI transaction commit thành công")
    void deleteDocument_deletesFileOnlyAfterCommit() throws Exception {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.deleteDocument(docId, ownerId);

            // DB record bị xóa trong transaction
            verify(legalDocumentRepository).delete(doc);
            verify(legalDocumentRepository).flush();
            // Nhưng TRƯỚC KHI commit thì file vật lý TUYỆT ĐỐI CHƯA ĐƯỢC XÓA
            verify(fileStorageService, never()).deleteFile(any());

            // Giả lập Spring commit transaction
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .getSynchronizations().forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            // Sau commit thì file vật lý mới được xóa
            verify(fileStorageService).deleteFile("uploads/encrypted_dichuc.bin");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("deleteDocument (M2a): Lỗi xóa file vật lý trong afterCommit không làm hỏng request (không ném 500)")
    void deleteDocument_fileDeleteFails_afterCommit_doesNotThrow() throws Exception {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        doThrow(new RuntimeException("Disk error on file deletion")).when(fileStorageService).deleteFile(any());

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.deleteDocument(docId, ownerId);
            verify(legalDocumentRepository).delete(doc);

            // Giả lập commit: afterCommit bị lỗi xóa file nhưng KHÔNG ném exception ra ngoài
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .getSynchronizations().forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("deleteDocument (M2 - Khóa ngoại): DB flush ném DataIntegrityViolationException -> Trả 409 CONFLICT")
    void deleteDocument_foreignKeyConflict_throwsConflict() {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .fileName("dichuc_2026.pdf")
                .fileUrlEncrypted("uploads/encrypted_dichuc.bin")
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("FK constraint violation"))
                .when(legalDocumentRepository).flush();

        assertThatThrownBy(() -> documentService.deleteDocument(docId, ownerId))
                .isInstanceOf(VaultException.class)
                .hasMessageContaining("The document is used in a verification request and cannot be deleted.")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        // Đảm bảo không đăng ký xóa file
        verify(fileStorageService, never()).deleteFile(any());
    }

    @Test
    @DisplayName("deleteDocument (m3): Bị chặn bởi VaultModificationGuard (DMS triggered / có hồ sơ xác minh mở) -> Trả 409 CONFLICT")
    void deleteDocument_guardBlocked_throwsConflict() {
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(mockVault)
                .build();

        when(legalDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        doThrow(new VaultException("Cannot modify vault while a verification request is pending or approved", HttpStatus.CONFLICT))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> documentService.deleteDocument(docId, ownerId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        verify(legalDocumentRepository, never()).delete(any());
        verify(fileStorageService, never()).deleteFile(any());
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

    // =========================================================================
    // X5: Versioning Tests
    // =========================================================================

    @Test
    @DisplayName("X5: uploadDocument bản đầu tiên (documentGroupId null) -> sinh group mới, version = 1, isCurrent = true")
    void uploadDocument_FirstVersion_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "dichuc_v1.pdf", "application/pdf", "noi dung di chuc v1".getBytes());
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(cryptoService.encryptBytes(any())).thenReturn("encrypted_v1".getBytes());
        when(fileStorageService.storeFile(any(), any())).thenReturn("/storage/dichuc_v1.pdf");
        when(legalDocumentRepository.save(any(LegalDocument.class))).thenAnswer(invocation -> {
            LegalDocument d = invocation.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        LegalDocument result = documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, file);

        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.isCurrent()).isTrue();
        assertThat(result.getDocumentGroupId()).isNotNull();
        verify(legalDocumentRepository).save(any(LegalDocument.class));
    }

    @Test
    @DisplayName("X5 (m2): uploadDocument phiên bản mới (v2) cùng documentGroupId -> version = 2, giữ loại tài liệu của nhóm, bản cũ chuyển isCurrent = false")
    void uploadDocument_NewVersion_Success() throws Exception {
        UUID groupId = UUID.randomUUID();
        LegalDocument v1Doc = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v1.pdf")
                .version(1)
                .isCurrent(true)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "dichuc_v2.pdf", "application/pdf", "noi dung di chuc v2 sua doi".getBytes());
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByDocumentGroupIdOrderByVersionDesc(groupId)).thenReturn(List.of(v1Doc));
        when(cryptoService.encryptBytes(any())).thenReturn("encrypted_v2".getBytes());
        when(fileStorageService.storeFile(any(), any())).thenReturn("/storage/dichuc_v2.pdf");
        when(legalDocumentRepository.save(any(LegalDocument.class))).thenAnswer(invocation -> {
            LegalDocument d = invocation.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });

        // Gửi kèm IDENTITY_CARD nhưng nhóm gốc là WILL -> phải giữ WILL theo m2
        LegalDocument result = documentService.uploadDocument(ownerId, vaultId, DocumentType.IDENTITY_CARD, file, groupId);

        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.isCurrent()).isTrue();
        assertThat(result.getDocumentGroupId()).isEqualTo(groupId);
        assertThat(result.getDocumentType()).isEqualTo(DocumentType.WILL);
        assertThat(v1Doc.isCurrent()).isFalse();
    }

    @Test
    @DisplayName("X5: uploadDocument bản mới với groupId không tồn tại -> Báo lỗi 404 NOT_FOUND")
    void uploadDocument_NewVersion_GroupNotFound_Throws404() {
        UUID nonExistentGroupId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "dichuc_v2.pdf", "application/pdf", "content".getBytes());
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByDocumentGroupIdOrderByVersionDesc(nonExistentGroupId)).thenReturn(List.of());

        assertThatThrownBy(() -> documentService.uploadDocument(ownerId, vaultId, DocumentType.WILL, file, nonExistentGroupId))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("X5 (m3): getDocumentsByVault lấy bản hiện hành và đếm phiên bản bằng 1 query gom nhóm")
    void getDocumentsByVault_AggregatesVersionCountsInOneQuery() {
        UUID groupId = UUID.randomUUID();
        LegalDocument v2 = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v2.pdf")
                .version(2)
                .isCurrent(true)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByVaultIdAndIsCurrentTrueOrderByUploadedAtDescIdDesc(vaultId))
                .thenReturn(List.of(v2));
        when(legalDocumentRepository.countVersionsByGroup(vaultId))
                .thenReturn(List.<Object[]>of(new Object[]{groupId, 2L}));

        List<DocumentResponse> result = documentService.getDocumentsByVault(vaultId, ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVersion()).isEqualTo(2);
        assertThat(result.get(0).getTotalVersions()).isEqualTo(2L);
        verify(legalDocumentRepository, never()).countByDocumentGroupId(any());
    }

    @Test
    @DisplayName("X5: getDocumentVersions -> Trả về danh sách tất cả các phiên bản của nhóm")
    void getDocumentVersions_Success() {
        UUID groupId = UUID.randomUUID();
        LegalDocument v2 = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v2.pdf")
                .version(2)
                .isCurrent(true)
                .uploadedAt(LocalDateTime.now())
                .build();
        LegalDocument v1 = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v1.pdf")
                .version(1)
                .isCurrent(false)
                .uploadedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(legalDocumentRepository.findByDocumentGroupIdOrderByVersionDesc(groupId)).thenReturn(List.of(v2, v1));

        List<DocumentResponse> versions = documentService.getDocumentVersions(vaultId, groupId, ownerId);

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo(2);
        assertThat(versions.get(0).isCurrent()).isTrue();
        assertThat(versions.get(1).getVersion()).isEqualTo(1);
        assertThat(versions.get(1).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("X5: deleteDocument bản v2 (current) -> Bản v1 còn lại được tự động thăng hạng lên isCurrent = true")
    void deleteDocument_CurrentVersion_PromotesPreviousVersion() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID v2Id = UUID.randomUUID();
        LegalDocument v2 = LegalDocument.builder()
                .id(v2Id)
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v2.pdf")
                .version(2)
                .isCurrent(true)
                .fileUrlEncrypted("/storage/v2.pdf")
                .build();

        LegalDocument v1 = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v1.pdf")
                .version(1)
                .isCurrent(false)
                .fileUrlEncrypted("/storage/v1.pdf")
                .build();

        when(legalDocumentRepository.findById(v2Id)).thenReturn(Optional.of(v2));
        when(legalDocumentRepository.findFirstByDocumentGroupIdOrderByVersionDesc(groupId)).thenReturn(Optional.of(v1));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            documentService.deleteDocument(v2Id, ownerId);
            verify(legalDocumentRepository).delete(v2);
            assertThat(v1.isCurrent()).isTrue();
            verify(legalDocumentRepository).saveAndFlush(v1);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("X5 (m5): toResponse lấy totalVersions thực tế từ DB khi bản cũ đã bị xóa")
    void toResponse_UsesActualRemainingVersionCount() {
        UUID groupId = UUID.randomUUID();
        LegalDocument v2Only = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(mockVault)
                .documentGroupId(groupId)
                .documentType(DocumentType.WILL)
                .fileName("dichuc_v2.pdf")
                .version(2)
                .isCurrent(true)
                .build();

        when(legalDocumentRepository.countByDocumentGroupId(groupId)).thenReturn(1L);

        DocumentResponse response = documentService.toResponse(v2Only);

        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getTotalVersions()).isEqualTo(1L);
        assertThat(response.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("X5 (M1): LegalDocument tạo trực tiếp không qua service -> @PrePersist tự gán documentGroupId và version = 1")
    void legalDocument_PrePersist_SetsDefaultGroupAndVersion() throws Exception {
        LegalDocument doc = LegalDocument.builder()
                .vault(mockVault)
                .documentType(DocumentType.WILL)
                .fileName("test.pdf")
                .fileUrlEncrypted("uploads/test.bin")
                .build();

        assertThat(doc.getDocumentGroupId()).isNull();
        var method = LegalDocument.class.getDeclaredMethod("defaultDocumentGroup");
        method.setAccessible(true);
        method.invoke(doc);

        assertThat(doc.getDocumentGroupId()).isNotNull();
        assertThat(doc.getVersion()).isEqualTo(1);
        assertThat(doc.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("X5: Các phương thức derived query trong LegalDocumentRepository khớp hoàn toàn với thuộc tính của LegalDocument")
    void repositoryDerivedQueries_MatchEntityProperties() {
        List<String> derivedMethods = List.of(
                "findByVaultId",
                "findByIdAndVaultId",
                "findByIdAndVaultIdAndIsCurrentTrue",
                "findByVaultIdOrderByUploadedAtDescIdDesc",
                "findByVaultIdAndIsCurrentTrueOrderByUploadedAtDescIdDesc",
                "findByDocumentGroupIdOrderByVersionDesc",
                "findFirstByDocumentGroupIdOrderByVersionDesc",
                "countByDocumentGroupId",
                "countByVaultId",
                "countByVaultIdAndIsCurrentTrue"
        );
        for (String name : derivedMethods) {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new org.springframework.data.repository.query.parser.PartTree(name, LegalDocument.class),
                    "Invalid derived query method: " + name
            );
        }
    }
}
