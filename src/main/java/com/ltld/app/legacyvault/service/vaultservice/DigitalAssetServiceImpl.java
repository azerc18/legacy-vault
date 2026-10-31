package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalAssetServiceImpl implements DigitalAssetService {

    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultRepository vaultRepository;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;
    private final VaultModificationGuard vaultModificationGuard;
    private final AssetAllocationRepository assetAllocationRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final EntitlementService entitlementService;

    // =========================================================
    // FR-27: Xem danh sách tài sản ACTIVE của Vault
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public List<DigitalAssetResponse> listAssets(UUID ownerId, UUID vaultId) {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        List<DigitalAsset> assets = digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE);
        Map<UUID, List<AssetAllocation>> byAsset = assetAllocationRepository
                .findByAssetIdIn(assets.stream().map(DigitalAsset::getId).toList()).stream()
                .collect(Collectors.groupingBy(al -> al.getAsset().getId()));
        return assets.stream()
                .map(a -> toResponse(a, byAsset.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    // =========================================================
    // FR-27: Xem chi tiết 1 tài sản (giải mã secret)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public DigitalAssetDetailResponse getAsset(UUID ownerId, UUID vaultId, UUID assetId) throws Exception {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            throw new VaultException("The vault contents were deleted under the retention policy.", HttpStatus.GONE);
        }
        DigitalAsset asset = findActiveAsset(assetId, vaultId);

        String secret;
        String notes;
        try {
            secret = cryptoService.decrypt(asset.getEncryptedSecret());
            notes = asset.getNotesEncrypted() != null
                    ? cryptoService.decrypt(asset.getNotesEncrypted())
                    : null;
        } catch (Exception ex) {
            log.error("Failed to decrypt digital asset {}: {}", assetId, ex.getMessage());
            throw new VaultException("Could not decrypt the digital asset content", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String ownerEmail = vault.getOwner() != null ? vault.getOwner().getEmail() : null;
        auditLogService.log(AuditAction.ASSET_VIEWED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vaultId, "DigitalAsset", assetId.toString(), null);

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
    // FR-27: Thêm tài sản mới
    // =========================================================
    @Override
    @Transactional
    public DigitalAssetResponse addAsset(UUID ownerId, UUID vaultId, DigitalAssetRequest request) throws Exception {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        // assertHandoverNotStarted gọi qua guard (bao gồm cả TRIGGERED và verification mở)
        vaultModificationGuard.ensureModifiable(vault);

        if (request.getSecret() == null || request.getSecret().isBlank()) {
            throw new VaultException("Secret is required when adding a new asset", HttpStatus.BAD_REQUEST);
        }

        DigitalAsset asset = DigitalAsset.builder()
                .vault(vault)
                .assetType(request.getAssetType())
                .assetName(request.getAssetName())
                .encryptedSecret(cryptoService.encrypt(request.getSecret()))
                .encryptionKeyRef(CryptoService.CURRENT_KEY_REF)
                .notesEncrypted(request.getNotes() != null && !request.getNotes().isBlank()
                        ? cryptoService.encrypt(request.getNotes()) : null)
                .status(AssetStatus.ACTIVE)
                .build();

        asset = digitalAssetRepository.saveAndFlush(asset);
        if (request.getAllocation() != null) applyAllocation(vault, asset, ownerId, request.getAllocation());

        String ownerEmail = vault.getOwner() != null ? vault.getOwner().getEmail() : null;
        auditLogService.log(AuditAction.ASSET_CREATED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vaultId, "DigitalAsset", asset.getId().toString(), null);

        return toResponse(asset, assetAllocationRepository.findByAssetId(asset.getId()));
    }

    // =========================================================
    // FR-27: Cập nhật tài sản
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

        // FR-27: null = giữ nguyên, "" = xóa ghi chú cũ
        if (request.getNotes() != null) {
            asset.setNotesEncrypted(request.getNotes().isBlank() ? null : cryptoService.encrypt(request.getNotes()));
        } else if (!allowsProportional(asset) && assetAllocationRepository.findByAssetId(assetId).stream()
                .anyMatch(al -> al.getMode() == AllocationMode.PROPORTIONAL)) {
            throw new VaultException(ErrorCode.ALLOCATION_MODE_NOT_ALLOWED,
                    "This asset type no longer allows PROPORTIONAL. Reassign it first.");
        }

        asset = digitalAssetRepository.saveAndFlush(asset);
        if (request.getAllocation() != null) applyAllocation(vault, asset, ownerId, request.getAllocation());

        String ownerEmail = vault.getOwner() != null ? vault.getOwner().getEmail() : null;
        auditLogService.log(AuditAction.ASSET_UPDATED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vaultId, "DigitalAsset", assetId.toString(), null);

        return toResponse(asset, assetAllocationRepository.findByAssetId(asset.getId()));
    }

    // =========================================================
    // FR-27: Xóa cứng tài sản (hard-delete, nhất quán với deleteVault)
    // =========================================================
    @Override
    @Transactional
    public void deleteAsset(UUID ownerId, UUID vaultId, UUID assetId) {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        DigitalAsset asset = findActiveAsset(assetId, vaultId);

        // Xóa và flush DB thành công trước khi ghi audit (tránh audit ma khi rollback)
        assetAllocationRepository.deleteByAssetId(assetId);
        digitalAssetRepository.delete(asset);
        digitalAssetRepository.flush();

        String ownerEmail = vault.getOwner() != null ? vault.getOwner().getEmail() : null;
        auditLogService.log(AuditAction.ASSET_DELETED, AuditResult.SUCCESS,
                ownerId, ownerEmail, vaultId, "DigitalAsset", assetId.toString(), null);
    }

    // =========================================================
    // Helpers
    // =========================================================

    // N2-owner: kiểm tra rồi ghi lại toàn bộ dòng phân bổ của tài sản
    private void applyAllocation(Vault vault, DigitalAsset asset, UUID ownerId,
                                 DigitalAssetRequest.AllocationRequest req) {
        List<DigitalAssetRequest.Recipient> recipients = req.getRecipients();
        if (recipients == null || recipients.isEmpty()) {
            assetAllocationRepository.deleteByAssetId(asset.getId());   // quay về mặc định: thuộc tất cả
            return;
        }
        if (req.getMode() == null) {
            throw new VaultException(ErrorCode.ASSET_FIELD_INVALID, "allocation.mode is required");
        }

        Map<UUID, VaultBeneficiary> inVault = vaultBeneficiaryRepository
                .findByVaultIdOrderByCreatedAtAsc(vault.getId()).stream()
                .collect(Collectors.toMap(VaultBeneficiary::getId, Function.identity()));

        Set<UUID> seen = new HashSet<>();
        for (DigitalAssetRequest.Recipient r : recipients) {
            requireInVault(inVault, r.getBeneficiaryId());
            if (!seen.add(r.getBeneficiaryId())) {
                throw new VaultException(ErrorCode.ASSET_FIELD_INVALID, "Duplicate recipient");
            }
            UUID cid = r.getContingentBeneficiaryId();
            if (cid != null) {
                requireInVault(inVault, cid);
                if (cid.equals(r.getBeneficiaryId())) {
                    throw new VaultException(ErrorCode.ASSET_FIELD_INVALID,
                            "Contingent beneficiary must differ from the primary one");
                }
            }
        }

        switch (req.getMode()) {
            case JOINT_CONSENSUS -> throw new VaultException(ErrorCode.ALLOCATION_MODE_NOT_ALLOWED,
                    "JOINT_CONSENSUS is not available yet");
            case EXCLUSIVE -> {
                if (recipients.size() != 1) {
                    throw new VaultException(ErrorCode.ASSET_FIELD_INVALID, "EXCLUSIVE needs exactly one recipient");
                }
                if (recipients.get(0).getContingentBeneficiaryId() != null) {
                    entitlementService.assertCanUseAdvancedAllocation(ownerId);
                }
            }
            case PROPORTIONAL -> {
                entitlementService.assertCanUseAdvancedAllocation(ownerId);
                if (!allowsProportional(asset)) {
                    throw new VaultException(ErrorCode.ALLOCATION_MODE_NOT_ALLOWED,
                            "PROPORTIONAL is only allowed for legal-claim assets");
                }
                BigDecimal total = BigDecimal.ZERO;
                for (DigitalAssetRequest.Recipient r : recipients) {
                    if (r.getContingentBeneficiaryId() != null) {
                        throw new VaultException(ErrorCode.ALLOCATION_MODE_NOT_ALLOWED,
                                "Contingent beneficiary is only for EXCLUSIVE");
                    }
                    if (r.getSharePercent() == null || r.getSharePercent().signum() <= 0) {
                        throw new VaultException(ErrorCode.ALLOCATION_PERCENT_INVALID, "Each share must be > 0");
                    }
                    total = total.add(r.getSharePercent());
                }
                if (recipients.size() < 2 || total.compareTo(new BigDecimal("100")) != 0) {
                    throw new VaultException(ErrorCode.ALLOCATION_PERCENT_INVALID,
                            "PROPORTIONAL needs at least 2 recipients and shares must total 100%");
                }
            }
        }

        assetAllocationRepository.deleteByAssetId(asset.getId());
        for (DigitalAssetRequest.Recipient r : recipients) {
            VaultBeneficiary contingent = r.getContingentBeneficiaryId() == null
                    ? null : inVault.get(r.getContingentBeneficiaryId());
            assetAllocationRepository.save(AssetAllocation.builder()
                    .asset(asset)
                    .beneficiary(inVault.get(r.getBeneficiaryId()))
                    .contingentBeneficiary(contingent)
                    .mode(req.getMode())
                    .sharePercent(req.getMode() == AllocationMode.EXCLUSIVE
                            ? new BigDecimal("100.00") : r.getSharePercent())
                    .note(req.getNote())
                    .build());
        }
    }

    private static void requireInVault(Map<UUID, VaultBeneficiary> inVault, UUID id) {
        if (!inVault.containsKey(id)) {
            throw new VaultException("Beneficiary not found in this vault", HttpStatus.NOT_FOUND);
        }
    }

    // Tạm: N3 (Duy) gán transferMechanism rồi thì bỏ nhánh fallback theo assetType
    private static boolean allowsProportional(DigitalAsset asset) {
        if (asset.getTransferMechanism() != null) {
            return asset.getTransferMechanism() == TransferMechanism.LEGAL_CLAIM_DOSSIER;
        }
        return asset.getAssetType() == AssetType.BANK_ACCOUNT || asset.getAssetType() == AssetType.DOCUMENT;
    }

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

    private static DigitalAssetResponse toResponse(DigitalAsset asset, List<AssetAllocation> rows) {
        return DigitalAssetResponse.builder()
                .id(asset.getId())
                .assetType(asset.getAssetType())
                .assetName(asset.getAssetName())
                .status(asset.getStatus())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .allocations(rows.stream().map(al -> DigitalAssetResponse.Allocation.builder()
                        .beneficiaryId(al.getBeneficiary().getId())
                        .contingentBeneficiaryId(al.getContingentBeneficiary() == null
                                ? null : al.getContingentBeneficiary().getId())
                        .mode(al.getMode())
                        .sharePercent(al.getSharePercent())
                        .note(al.getNote())
                        .build()).toList())
                .build();
    }
}
