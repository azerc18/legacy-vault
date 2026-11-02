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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    // Whitelist Content-Type cho file tải về (khớp với FR-04/FR-18)
    private static final Map<String, String> DOCUMENT_MIME_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private final LegalDocumentRepository legalDocumentRepository;
    private final UserRepository userRepository;
    private final VaultRepository vaultRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final VaultModificationGuard vaultModificationGuard;

    @Override
    @Transactional
    public LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception {
        return uploadDocument(ownerId, vaultId, type, file, null);
    }

    @Override
    @Transactional
    public LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file, UUID documentGroupId) throws Exception {
        // 0. Validate File
        if (file == null || file.isEmpty()) {
            throw new VaultException("File cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new VaultException("File size exceeds 10MB limit");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new VaultException("Invalid file format. Only PDF, DOC/DOCX, and JPG/PNG are allowed.");
        }

        // 1. Kiểm tra Két sắt (Vault) có tồn tại và đúng là của người này không
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        if (!vault.getOwner().getId().equals(ownerId)) {
            // Tra email của KẺ GỌI (attacker), không phải email chủ vault
            String attackerEmail = userRepository.findById(ownerId)
                    .map(User::getEmail).orElse("unknown");
            auditLogService.failure(AuditAction.DOCUMENT_UPLOADED, ownerId, attackerEmail, "Unauthorized: You don't own this vault");
            throw new VaultException("Vault not found", HttpStatus.NOT_FOUND);
        }

        // FR-04: Áp dụng quy tắc chặn sửa đổi Vault dùng chung (ACTIVE, DMS triggered, hồ sơ xác minh mở)
        vaultModificationGuard.ensureModifiable(vault);

        // 2. Xác định document_group_id và version theo nghiệp vụ X5
        UUID targetGroupId;
        int targetVersion = 1;

        if (documentGroupId == null) {
            // Tài liệu mới hoàn toàn -> sinh group id mới, version = 1
            targetGroupId = UUID.randomUUID();
        } else {
            // Tải lên bản sửa đổi/thay thế cho một nhóm tài liệu đã có
            // Bắt buộc nhóm này phải tồn tại trong đúng vault này
            List<LegalDocument> existingGroupDocs = legalDocumentRepository.findByDocumentGroupIdOrderByVersionDesc(documentGroupId);
            if (existingGroupDocs.isEmpty() || !existingGroupDocs.get(0).getVault().getId().equals(vaultId)) {
                throw new VaultException("Nhóm tài liệu không tồn tại trong két này.", HttpStatus.NOT_FOUND);
            }

            // Tính version tiếp theo = max(version hiện có) + 1
            targetVersion = existingGroupDocs.get(0).getVersion() + 1;
            targetGroupId = documentGroupId;

            // Đánh dấu các phiên bản cũ của nhóm thành is_current = false
            for (LegalDocument oldDoc : existingGroupDocs) {
                if (oldDoc.isCurrent()) {
                    oldDoc.setCurrent(false);
                    legalDocumentRepository.save(oldDoc);
                }
            }
        }

        // 3. Ép file thành mảng byte gốc, rồi ném vào CryptoService để MÃ HÓA
        byte[] encryptedFileData = cryptoService.encryptBytes(file.getBytes());

        // 4. Đưa mảng byte đã mã hóa cho FileStorageService lưu xuống đĩa/cloud (lấy đường dẫn trả về)
        String fileUrl = fileStorageService.storeFile(file, encryptedFileData);

        // 5. Lưu thông tin (metadata) của tài liệu vào Database
        LegalDocument document = LegalDocument.builder()
                .documentGroupId(targetGroupId)
                .vault(vault)
                .documentType(type)
                .fileUrlEncrypted(fileUrl) // Đường dẫn trên ổ cứng/cloud
                .fileName(file.getOriginalFilename()) // Tên file lúc khách hàng chọn upload
                .version(targetVersion)
                .isCurrent(true)
                .build();

        LegalDocument saved = legalDocumentRepository.save(document);

        // Log thành công kèm targetType và targetId
        auditLogService.log(AuditAction.DOCUMENT_UPLOADED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vault.getId(),
                "Vault", vault.getId().toString(),
                "Document ID: " + (saved.getId() != null ? saved.getId().toString() : "new") + " (v" + saved.getVersion() + ", group: " + targetGroupId + ")");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByVault(UUID vaultId, UUID ownerId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new VaultException("Vault not found", HttpStatus.NOT_FOUND);
        }

        // X5: Danh sách tài liệu hiện hành của Vault (lọc is_current = true)
        return legalDocumentRepository.findByVaultIdOrderByUploadedAtDescIdDesc(vaultId)
                .stream()
                .filter(LegalDocument::isCurrent)
                .map(doc -> {
                    long totalVersions = doc.getDocumentGroupId() != null
                            ? legalDocumentRepository.countByDocumentGroupId(doc.getDocumentGroupId())
                            : 1L;
                    if (totalVersions <= 0) {
                        totalVersions = 1L;
                    }
                    return DocumentResponse.builder()
                            .id(doc.getId())
                            .vaultId(vaultId)
                            .documentGroupId(doc.getDocumentGroupId())
                            .documentType(doc.getDocumentType())
                            .fileName(doc.getFileName())
                            .uploadedAt(doc.getUploadedAt())
                            .version(doc.getVersion() > 0 ? doc.getVersion() : 1)
                            .isCurrent(doc.isCurrent())
                            .totalVersions(totalVersions)
                            .archived(vault.getStatus() == VaultStatus.ARCHIVED_LOCKED)
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentVersions(UUID vaultId, UUID documentGroupId, UUID ownerId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new VaultException("Vault not found", HttpStatus.NOT_FOUND);
        }

        List<LegalDocument> versions = legalDocumentRepository.findByDocumentGroupIdOrderByVersionDesc(documentGroupId);
        if (versions.isEmpty() || !versions.get(0).getVault().getId().equals(vaultId)) {
            throw new VaultException("Nhóm tài liệu không tồn tại trong két này.", HttpStatus.NOT_FOUND);
        }

        long totalVersions = versions.size();
        return versions.stream()
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .vaultId(vaultId)
                        .documentGroupId(doc.getDocumentGroupId())
                        .documentType(doc.getDocumentType())
                        .fileName(doc.getFileName())
                        .uploadedAt(doc.getUploadedAt())
                        .version(doc.getVersion())
                        .isCurrent(doc.isCurrent())
                        .totalVersions(totalVersions)
                        .archived(vault.getStatus() == VaultStatus.ARCHIVED_LOCKED)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownloadResponse downloadDocumentForOwner(UUID documentId, UUID ownerId) throws Exception {
        LegalDocument doc = legalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new VaultException("Document not found", HttpStatus.NOT_FOUND));
        Vault vault = doc.getVault();

        // m1: Trả 404 thay vì 403 để tránh lộ sự tồn tại tài liệu khi non-owner tra cứu
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new VaultException("Document not found", HttpStatus.NOT_FOUND);
        }

        // V2: Vault đã bị xóa vật lý (scheduler đã xóa file) -> trả 410 GONE thay vì để readFile() ném 500
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new VaultException("Nội dung két đã bị xóa theo chính sách lưu trữ.", HttpStatus.GONE);
        }

        byte[] encryptedBytes = fileStorageService.readFile(doc.getFileUrlEncrypted());
        byte[] decryptedBytes = cryptoService.decryptBytes(encryptedBytes);

        // m5: Whitelist Content-Type dựa trên đuôi file, mặc định application/octet-stream
        String contentType = resolveContentType(doc.getFileName());

        // Ghi Audit Log thành công
        auditLogService.log(AuditAction.DOCUMENT_DOWNLOADED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vault.getId(),
                "LegalDocument", doc.getId().toString(),
                "Owner downloaded document (v" + doc.getVersion() + ")");
        return DocumentDownloadResponse.builder()
                .fileName(doc.getFileName())
                .contentType(contentType)
                .content(decryptedBytes)
                .build();
    }

    @Override
    @Transactional
    public void deleteDocument(UUID documentId, UUID ownerId) throws Exception {
        LegalDocument doc = legalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new VaultException("Document not found", HttpStatus.NOT_FOUND));
        Vault vault = doc.getVault();

        // m1: Trả 404 thay vì 403 để tránh lộ sự tồn tại tài liệu khi non-owner tra cứu
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new VaultException("Document not found", HttpStatus.NOT_FOUND);
        }

        // m3: Áp dụng quy tắc chặn sửa đổi Vault dùng chung (ACTIVE, DMS, hồ sơ xác minh mở)
        vaultModificationGuard.ensureModifiable(vault);

        UUID groupId = doc.getDocumentGroupId();
        boolean wasCurrent = doc.isCurrent();
        String fileUrl = doc.getFileUrlEncrypted();

        // M2: Xóa bản ghi DB TRONG transaction; bắt khóa ngoại ném 409 CONFLICT
        try {
            legalDocumentRepository.delete(doc);
            legalDocumentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new VaultException("The document is used in a verification request and cannot be deleted.", HttpStatus.CONFLICT);
        }

        // X5: Nếu bản vừa bị xóa là bản hiện hành (is_current = true), tự động nâng phiên bản
        // mới nhất còn lại trong nhóm lên làm bản hiện hành để không bị mất tài liệu chính thức
        if (wasCurrent && groupId != null) {
            Optional<LegalDocument> latestRemaining = legalDocumentRepository.findFirstByDocumentGroupIdOrderByVersionDesc(groupId);
            if (latestRemaining.isPresent()) {
                LegalDocument promoted = latestRemaining.get();
                promoted.setCurrent(true);
                legalDocumentRepository.saveAndFlush(promoted);
            }
        }

        // M2a & M2b: Xóa file vật lý SAU KHI transaction commit thành công
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        fileStorageService.deleteFile(fileUrl);
                    } catch (RuntimeException e) {
                        log.warn("FR-04: document {} deleted but file not removed: {}", documentId, e.getMessage());
                    }
                }
            });
        } else {
            try {
                fileStorageService.deleteFile(fileUrl);
            } catch (RuntimeException e) {
                log.warn("FR-04: document {} deleted but file not removed: {}", documentId, e.getMessage());
            }
        }

        auditLogService.log(AuditAction.DOCUMENT_DELETED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vault.getId(),
                "LegalDocument", documentId.toString(), null);
    }

    private String resolveContentType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return DOCUMENT_MIME_TYPES.getOrDefault(ext, "application/octet-stream");
    }
}
