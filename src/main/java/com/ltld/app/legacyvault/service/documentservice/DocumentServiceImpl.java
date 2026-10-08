package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final LegalDocumentRepository legalDocumentRepository;
    private final VaultRepository vaultRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception {
        // 1. Kiểm tra Két sắt (Vault) có tồn tại và đúng là của người này không
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new RuntimeException("Vault not found"));

        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Unauthorized: You don't own this vault");
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

        return legalDocumentRepository.save(document);
    }
}
