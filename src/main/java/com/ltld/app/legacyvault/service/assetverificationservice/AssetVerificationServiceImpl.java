package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.dto.assetverificationdto.ConfirmVerificationRequest;
import com.ltld.app.legacyvault.dto.assetverificationdto.StartVerificationRequest;
import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetVerificationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * PR 0: KHUNG. Chỉ đọc trạng thái. start/confirm chưa có logic (làm ở bước "nền tảng").
 */
@Service
@RequiredArgsConstructor
public class AssetVerificationServiceImpl implements AssetVerificationService {

    private final AssetVerificationRepository verificationRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultRepository vaultRepository;
    // ObjectProvider để app vẫn khởi động được khi chưa có verifier nào (PR 0)
    private final ObjectProvider<AssetVerifier> verifiers;

    @Override
    @Transactional(readOnly = true)
    public AssetVerificationResponse getStatus(UUID ownerId, UUID vaultId, UUID assetId) {
        DigitalAsset asset = loadOwnedAsset(ownerId, vaultId, assetId);
        List<AssetVerificationMethod> methods = verifiers.orderedStream()
                .filter(v -> v.type() == asset.getAssetType())
                .flatMap(v -> v.methods().stream())
                .toList();

        return verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId)
                .map(v -> toResponse(assetId, v, methods))
                .orElseGet(() -> AssetVerificationResponse.builder()
                        .assetId(assetId)
                        .status(AssetVerificationStatus.SELF_DECLARED)
                        .availableMethods(methods)
                        .build());
    }

    @Override
    public AssetVerificationResponse start(UUID ownerId, UUID vaultId, UUID assetId,
                                           StartVerificationRequest request) {
        throw new VaultException("Asset verification is not implemented yet", HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public AssetVerificationResponse confirm(UUID ownerId, UUID vaultId, UUID assetId,
                                             ConfirmVerificationRequest request) {
        throw new VaultException("Asset verification is not implemented yet", HttpStatus.NOT_IMPLEMENTED);
    }

    // Sai chủ / sai vault / tài sản không ACTIVE đều trả 404 (giống DigitalAssetServiceImpl)
    private DigitalAsset loadOwnedAsset(UUID ownerId, UUID vaultId, UUID assetId) {
        vaultRepository.findById(vaultId)
                .filter(v -> v.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
        return digitalAssetRepository.findById(assetId)
                .filter(a -> a.getStatus() == AssetStatus.ACTIVE
                        && a.getVault().getId().equals(vaultId))
                .orElseThrow(() -> new VaultException("Asset not found", HttpStatus.NOT_FOUND));
    }

    private AssetVerificationResponse toResponse(UUID assetId, AssetVerification v,
                                                 List<AssetVerificationMethod> methods) {
        return AssetVerificationResponse.builder()
                .assetId(assetId)
                .status(v.getStatus())
                .method(v.getMethod())
                .provider(v.getProvider())
                .verifiedAt(v.getVerifiedAt())
                .expiresAt(v.getExpiresAt())
                .availableMethods(methods)
                .build();
    }
}