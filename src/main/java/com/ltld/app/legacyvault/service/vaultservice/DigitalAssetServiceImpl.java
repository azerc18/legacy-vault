package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalAssetServiceImpl implements DigitalAssetService {

    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultRepository vaultRepository;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;
    private final VaultModificationGuard vaultModificationGuard;

    // =========================================================
    // V1: Xem danh sách tài sản ACTIVE của Vault
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public List<DigitalAssetResponse> listAssets(UUID ownerId, UUID vaultId) {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        return digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE)
                .stream()
                .map(DigitalAssetServiceImpl::toResponse)
                .toList();
    }

    // =========================================================
    // V2: Xem chi tiết 1 tài sản (giải mã secret)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public DigitalAssetDetailResponse getAsset(UUID ownerId, UUID vaultId, UUID assetId) throws Exception {
        loadOwnedVault(vaultId, ownerId);
        DigitalAsset asset = findActiveAsset(assetId, vaultId);

        String secret = cryptoService.decrypt(asset.getEncryptedSecret());
        String notes = asset.getNotesEncrypted() != null
                ? cryptoService.decrypt(asset.getNotesEncrypted())
                : null;

        auditLogService.log(AuditAction.ASSET_VIEWED, AuditResult.SUCCESS,
                ownerId, null, vaultId, "DigitalAsset", assetId.toString(), null);

        return DigitalAssetDetailResponse.builder()
                .id(asset.getId())
                .assetType(asset.getAssetType())
                .assetName(asset.getAssetName())
                .secret(secret)
                .notes(notes)
                .status(asset.getStatus())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }

    // =========================================================
    // V4a: Thêm tài sản mới
    // =========================================================
    @Override
    @Transactional
    public DigitalAssetResponse addAsset(UUID ownerId, UUID vaultId, DigitalAssetRequest request) throws Exception {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        // assertHandoverNotStarted gọi qua guard (bao gồm cả TRIGGERED và verification mở)
        vaultModificationGuard.ensureModifiable(vault);

        if (request.getSecret() == null || request.getSecret().isBlank()) {
            throw new VaultException("Secret không được để trống khi thêm tài sản mới", HttpStatus.BAD_REQUEST);
        }

        DigitalAsset asset = DigitalAsset.builder()
                .vault(vault)
                .assetType(request.getAssetType())
                .assetName(request.getAssetName())
                .encryptedSecret(cryptoService.encrypt(request.getSecret()))
                .encryptionKeyRef(CryptoService.CURRENT_KEY_REF)
                .notesEncrypted(request.getNotes() != null ? cryptoService.encrypt(request.getNotes()) : null)
                .status(AssetStatus.ACTIVE)
                .build();

        asset = digitalAssetRepository.save(asset);

        auditLogService.log(AuditAction.ASSET_CREATED, AuditResult.SUCCESS,
                ownerId, null, vaultId, "DigitalAsset", asset.getId().toString(), null);

        return toResponse(asset);
    }

    // =========================================================
    // V4b: Cập nhật tài sản
    // =========================================================
    @Override
    @Transactional
    public DigitalAssetResponse updateAsset(UUID ownerId, UUID vaultId, UUID assetId,
                                            DigitalAssetRequest request) throws Exception {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        DigitalAsset asset = findActiveAsset(assetId, vaultId);

        asset.setAssetType(request.getAssetType());
        asset.setAssetName(request.getAssetName());

        // Chỉ cập nhật secret nếu client gửi giá trị mới
        if (request.getSecret() != null && !request.getSecret().isBlank()) {
            asset.setEncryptedSecret(cryptoService.encrypt(request.getSecret()));
            asset.setEncryptionKeyRef(CryptoService.CURRENT_KEY_REF);
        }

        // Cập nhật notes (null = xóa ghi chú cũ)
        asset.setNotesEncrypted(
                request.getNotes() != null ? cryptoService.encrypt(request.getNotes()) : null
        );

        asset = digitalAssetRepository.save(asset);

        auditLogService.log(AuditAction.ASSET_UPDATED, AuditResult.SUCCESS,
                ownerId, null, vaultId, "DigitalAsset", assetId.toString(), null);

        return toResponse(asset);
    }

    // =========================================================
    // V4c: Xóa cứng tài sản (hard-delete, nhất quán với deleteVault)
    // =========================================================
    @Override
    @Transactional
    public void deleteAsset(UUID ownerId, UUID vaultId, UUID assetId) {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        DigitalAsset asset = findActiveAsset(assetId, vaultId);

        // Ghi audit TRƯỚC khi xóa (sau khi xóa không còn entity để tham chiếu)
        auditLogService.log(AuditAction.ASSET_DELETED, AuditResult.SUCCESS,
                ownerId, null, vaultId, "DigitalAsset", assetId.toString(), null);

        digitalAssetRepository.delete(asset);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Load vault và kiểm tra ownership. Sai chủ → 404 (không leak thông tin vault tồn tại).
     */
    private Vault loadOwnedVault(UUID vaultId, UUID ownerId) {
        return vaultRepository.findById(vaultId)
                .filter(v -> v.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Tìm asset ACTIVE thuộc đúng vault. Không tìm thấy hoặc sai vault → 404.
     */
    private DigitalAsset findActiveAsset(UUID assetId, UUID vaultId) {
        return digitalAssetRepository.findByIdAndVaultIdAndStatus(assetId, vaultId, AssetStatus.ACTIVE)
                .orElseThrow(() -> new VaultException("Digital asset not found", HttpStatus.NOT_FOUND));
    }

    private static DigitalAssetResponse toResponse(DigitalAsset asset) {
        return DigitalAssetResponse.builder()
                .id(asset.getId())
                .assetType(asset.getAssetType())
                .assetName(asset.getAssetName())
                .status(asset.getStatus())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
