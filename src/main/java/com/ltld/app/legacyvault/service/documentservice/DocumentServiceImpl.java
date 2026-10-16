package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final LegalDocumentRepository legalDocumentRepository;
    private final UserRepository userRepository;
    private final VaultRepository vaultRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final VaultModificationGuard vaultModificationGuard;

    // FR-04: Upload tài liệu vào Vault (mã hóa E2E)
    @Override
    @Transactional
    public LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception {
        // 0. Validate file trước khi tra DB để tiết kiệm tài nguyên
        if (file == null || file.isEmpty()) {
            throw new VaultException("File cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new VaultException("File size exceeds 10MB limit");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new VaultException("Invalid file format. Only PDF, DOC/DOCX, and JPG/PNG are allowed.");
        }

        // 1. Kiểm tra Vault tồn tại và đúng chủ sở hữu — trả 404 nếu không phải chủ (tránh lộ ID)
        Vault vault = loadOwnedVault(vaultId, ownerId);

        // m3: Kiểm tra Vault có thể bị thay đổi (ACTIVE, DMS không trigger, không có verification mở)
        vaultModificationGuard.ensureModifiable(vault);

        // 2. Ép file thành mảng byte gốc, rồi ném vào CryptoService để MÃ HÓA
        byte[] encryptedFileData = cryptoService.encryptBytes(file.getBytes());

        // 3. Đưa mảng byte đã mã hóa cho FileStorageService lưu xuống đĩa (lấy đường dẫn trả về)
        String fileUrl = fileStorageService.storeFile(file, encryptedFileData);

        // 4. Lưu thông tin (metadata) của tài liệu vào Database
        LegalDocument document = LegalDocument.builder()
                .vault(vault)
                .documentType(type)
                .fileUrlEncrypted(fileUrl)
                .fileName(file.getOriginalFilename())
                .build();

        LegalDocument saved = legalDocumentRepository.save(document);

        // Ghi audit log thành công
        auditLogService.log(AuditAction.DOCUMENT_UPLOADED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "Document ID: " + saved.getId().toString());
        return saved;
    }

    // FR-04: Liệt kê tài liệu trong Vault (chỉ chủ sở hữu)
    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID ownerId, UUID vaultId) {
        // Trả 404 nếu không phải chủ — tránh lộ sự tồn tại của vault người khác
        loadOwnedVault(vaultId, ownerId);

        return legalDocumentRepository.findByVaultIdOrderByUploadedAtDescIdDesc(vaultId)
                .stream()
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .vaultId(vaultId)
                        .documentType(doc.getDocumentType())
                        .fileName(doc.getFileName())
                        .uploadedAt(doc.getUploadedAt())
                        .build())
                .toList();
    }

    // FR-04: Xóa tài liệu pháp lý khỏi Vault (chỉ chủ sở hữu, xóa file vật lý sau DB commit)
    @Override
    @Transactional
    public void deleteDocument(UUID ownerId, UUID vaultId, UUID documentId) {
        // Trả 404 nếu không phải chủ sở hữu vault
        Vault vault = loadOwnedVault(vaultId, ownerId);

        // m3: Vault phải ACTIVE, DMS chưa trigger, không có verification đang mở
        vaultModificationGuard.ensureModifiable(vault);

        // Tìm tài liệu — phải thuộc đúng vault để tránh xóa nhầm tài liệu của vault khác
        LegalDocument document = legalDocumentRepository.findByIdAndVaultId(documentId, vaultId)
                .orElseThrow(() -> new VaultException("Document not found", HttpStatus.NOT_FOUND));

        // Thu thập đường dẫn file TRƯỚC khi xóa DB
        String fileUrl = document.getFileUrlEncrypted();

        // Xóa DB trước — nếu rollback thì file vẫn còn nguyên, không mất tài liệu pháp lý
        legalDocumentRepository.delete(document);

        // Ghi audit log
        auditLogService.log(AuditAction.DOCUMENT_DELETED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vaultId,
                "LegalDocument", documentId.toString(), null);

        // Xóa file vật lý SAU KHI DB commit — method có @Transactional nên synchronization luôn active
        // M2a fix: bọc try/catch để lỗi xóa file không làm hỏng response (DB đã commit thành công)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileStorageService.deleteFile(fileUrl);
                } catch (RuntimeException e) {
                    // DB đã commit: để lại file mồ côi (vô hại), không làm trả 500 cho client
                    log.warn("FR-04/deleteDocument: file not removed after document deleted: {}", e.getMessage());
                }
            }
        });
    }

    // FR-18: Owner tải xuống tài liệu để xem (giải mã trong RAM, không lưu lại)
    @Override
    @Transactional(readOnly = true)
    public DocumentDownloadResponse downloadDocumentForOwner(UUID ownerId, UUID vaultId, UUID documentId) throws Exception {
        // Trả 404 nếu không phải chủ sở hữu vault
        loadOwnedVault(vaultId, ownerId);

        // Tìm tài liệu — phải thuộc đúng vault để tránh tải nhầm
        LegalDocument document = legalDocumentRepository.findByIdAndVaultId(documentId, vaultId)
                .orElseThrow(() -> new VaultException("Document not found", HttpStatus.NOT_FOUND));

        // Đọc nội dung file đã mã hóa từ đĩa
        byte[] encryptedContent = fileStorageService.readFile(document.getFileUrlEncrypted());

        // Giải mã trong RAM — không lưu lại bản giải mã
        byte[] decryptedContent = cryptoService.decryptBytes(encryptedContent);

        auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.SUCCESS,
                ownerId, null, vaultId,
                "LegalDocument", documentId.toString(), null);

        return DocumentDownloadResponse.builder()
                .fileName(document.getFileName())
                .contentType(guessMimeType(document.getFileName()))
                .content(decryptedContent)
                .build();
    }

    // Tải Vault và kiểm tra quyền sở hữu — trả 404 (không phải 403) để tránh lộ sự tồn tại của vault
    private Vault loadOwnedVault(UUID vaultId, UUID ownerId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
        if (!vault.getOwner().getId().equals(ownerId)) {
            // Không phân biệt "vault không tồn tại" và "không phải chủ" để chống ID enumeration
            throw new VaultException("Vault not found", HttpStatus.NOT_FOUND);
        }
        return vault;
    }

    // Đoán MIME type từ tên file để trả header Content-Type đúng cho browser
    private String guessMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}
