package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements VaultService {

    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional // Nếu có lỗi xảy ra ở bất kỳ dòng nào, DB sẽ undo lại toàn bộ (rollback)
    public Vault createVault(UUID ownerId, CreateVaultRequest request) throws Exception {
        // 1. Tìm thông tin chủ sở hữu (Owner)
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new VaultException("Owner not found", HttpStatus.NOT_FOUND));

        // 2. Tạo két sắt (Vault)
        Vault vault = Vault.builder()
                .owner(owner)
                .name(request.getName())
                .description(request.getDescription())
                .status(VaultStatus.ACTIVE)
                .build();

        vault = vaultRepository.save(vault);

        // 3. Nếu người dùng có gửi kèm danh sách tài sản, ta sẽ MÃ HÓA rồi lưu
        if (request.getAssets() != null) {
            for (CreateVaultRequest.DigitalAssetDto assetDto : request.getAssets()) {
                DigitalAsset asset = DigitalAsset.builder()
                        .vault(vault)
                        .assetType(assetDto.getAssetType())
                        .assetName(assetDto.getAssetName())
                        // ĐÂY LÀ CHỖ QUAN TRỌNG NHẤT: Gọi CryptoService để mã hóa chữ gốc thành loằng ngoằng
                        .encryptedSecret(cryptoService.encrypt(assetDto.getSecret()))
                        .encryptionKeyRef("default-master-key")
                        .notesEncrypted(assetDto.getNotes() != null ? cryptoService.encrypt(assetDto.getNotes()) : null)
                        .status(AssetStatus.ACTIVE)
                        .build();
                digitalAssetRepository.save(asset);
            }
        }

        auditLogService.log(AuditAction.VAULT_CREATED, AuditResult.SUCCESS, 
                ownerId, owner.getEmail(), vault.getId(),
                "Vault", vault.getId().toString(), null);
        return vault;
    }

    @Override
    @Transactional
    public void deleteVault(UUID vaultId, UUID ownerId) throws Exception {
        // 1. Tìm Vault — dùng vault.getOwner() để lấy email, tránh query UserRepository thừa (#16)
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        String ownerEmail = vault.getOwner().getEmail();

        // 2. Bảo mật: Két của ai người nấy xóa
        if (!vault.getOwner().getId().equals(ownerId)) {
            auditLogService.failure(AuditAction.VAULT_DELETE_DENIED, ownerId, ownerEmail, "Unauthorized: You don't own this vault");
            throw new VaultException("Unauthorized: You don't own this vault", HttpStatus.FORBIDDEN);
        }

        // 3. Nghiệp vụ: Trạng thái không phải ACTIVE thì từ chối xóa
        if (vault.getStatus() != VaultStatus.ACTIVE) {
            auditLogService.failure(AuditAction.VAULT_DELETE_DENIED, ownerId, ownerEmail, "Cannot delete vault that is not in active state");
            throw new VaultException("Cannot delete vault that is not in active state");
        }

        // 4. Thu thập đường dẫn file TRƯỚC khi xóa DB — N1 fix
        List<LegalDocument> docs = legalDocumentRepository.findByVaultId(vaultId);
        List<String> filesToDelete = docs.stream().map(LegalDocument::getFileUrlEncrypted).toList();

        // Xóa DB bằng batch (nhanh hơn forEach) — ràng buộc FK theo thứ tự
        legalDocumentRepository.deleteAllInBatch(docs);
        digitalAssetRepository.deleteAllInBatch(digitalAssetRepository.findByVaultId(vaultId));

        // 5. Đập cái két (Vault)
        vaultRepository.delete(vault);

        // N1: Chỉ xóa file vật lý SAU KHI DB commit thành công
        // Nếu bước trên rollback → file vẫn còn nguyên, không mất dữ liệu pháp lý
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    filesToDelete.forEach(fileStorageService::deleteFile);
                }
            });
        } else {
            // Khi chạy ngoài Spring transaction (ví dụ: trong unit test)
            filesToDelete.forEach(fileStorageService::deleteFile);
        }

        auditLogService.log(AuditAction.VAULT_DELETED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vault.getId(),
                "Vault", vaultId.toString(), null);
    }
}
