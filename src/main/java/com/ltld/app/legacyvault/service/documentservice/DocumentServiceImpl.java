package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

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

    @Override
    @Transactional
    public LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception {
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
            throw new VaultException("Unauthorized: You don't own this vault", HttpStatus.FORBIDDEN);
        }

        // Chỉ cho phép upload khi Vault đang ACTIVE (chưa bị DMS kích hoạt, chưa bàn giao)
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            throw new VaultException("Cannot upload document to vault in " + vault.getStatus() + " state");
        }

        // 2. Ép file thành mảng byte gốc, rồi ném vào CryptoService để MÃ HÓA
        byte[] encryptedFileData = cryptoService.encryptBytes(file.getBytes());

        // 3. Đưa mảng byte đã mã hóa cho FileStorageService lưu xuống đĩa (lấy đường dẫn trả về)
        String fileUrl = fileStorageService.storeFile(file, encryptedFileData);

        // 4. Lưu thông tin (metadata) của tài liệu vào Database
        LegalDocument document = LegalDocument.builder()
                .vault(vault)
                .documentType(type)
                .fileUrlEncrypted(fileUrl) // Đường dẫn trên ổ cứng
                .fileName(file.getOriginalFilename()) // Tên file lúc khách hàng chọn upload
                .build();

        LegalDocument saved = legalDocumentRepository.save(document);

        // Log thành công kèm targetType và targetId
        auditLogService.log(AuditAction.DOCUMENT_UPLOADED, AuditResult.SUCCESS,
                ownerId, vault.getOwner().getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), "Document ID: " + saved.getId().toString());
        return saved;
    }
}
