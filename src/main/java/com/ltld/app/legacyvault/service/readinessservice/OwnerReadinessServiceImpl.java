package com.ltld.app.legacyvault.service.readinessservice;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessItemResponse;
import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.TransferMechanisms;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * F1: chấm điểm theo mô hình N:M (N2a/N2-owner/N3):
 * - "Vault có người thụ hưởng" = có ít nhất 1 dòng vault_beneficiaries;
 * - "Tài sản đã phân bổ" = tài sản ACTIVE có ít nhất 1 dòng asset_allocations (phân bổ tường minh).
 * Không đọc cột beneficiary cũ của Vault. Số truy vấn cố định, không phụ thuộc số vault/tài sản.
 */
@Service
@RequiredArgsConstructor
public class OwnerReadinessServiceImpl implements OwnerReadinessService {

    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final AssetAllocationRepository assetAllocationRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public ReadinessResponse evaluateReadiness(UUID ownerId) {
        List<Vault> vaults = vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        List<UUID> vaultIds = vaults.stream().map(Vault::getId).toList();

        List<DigitalAsset> activeAssets;
        Set<UUID> vaultsWithBeneficiary;
        Set<UUID> allocatedAssetIds;
        Set<UUID> vaultsWithWill;
        if (vaultIds.isEmpty()) {
            activeAssets = List.of();
            vaultsWithBeneficiary = Set.of();
            allocatedAssetIds = Set.of();
            vaultsWithWill = Set.of();
        } else {
            activeAssets = digitalAssetRepository.findByVaultIdInAndStatus(vaultIds, AssetStatus.ACTIVE);
            vaultsWithBeneficiary = new HashSet<>(vaultBeneficiaryRepository.findVaultIdsWithBeneficiaryByOwnerId(ownerId));
            allocatedAssetIds = activeAssets.isEmpty()
                    ? Set.of()
                    : new HashSet<>(assetAllocationRepository.findAllocatedActiveAssetIdsByOwnerId(ownerId));
            vaultsWithWill = new HashSet<>(
                    legalDocumentRepository.findVaultIdsWithDocumentTypeByOwnerId(ownerId, DocumentType.WILL));
        }

        Map<UUID, List<DigitalAsset>> assetsByVault = activeAssets.stream()
                .collect(Collectors.groupingBy(a -> a.getVault().getId()));

        List<ExecutorAssignment> executors = executorAssignmentRepository.findAllByOwnerId(ownerId);
        Optional<DmsConfig> dmsOpt = dmsConfigRepository.findByOwnerId(ownerId);

        String defaultVaultUrl = vaults.isEmpty()
                ? "/owner/vaults/new"
                : vaultUrl(vaults.get(0).getId());

        List<ReadinessItemResponse> items = new ArrayList<>();

        // 1. Đã tạo Master Vault và có ít nhất 1 tài sản số ACTIVE (+20đ)
        boolean hasVaultAndAsset = !vaults.isEmpty() && !activeAssets.isEmpty();
        items.add(item("MASTER_VAULT_AND_ASSET", "masterVaultAndAsset",
                "Create Master Estate Vault and add at least 1 digital asset",
                hasVaultAndAsset, 20, defaultVaultUrl));

        // 2. Mọi vault chứa tài sản đều có người thụ hưởng và mọi tài sản ACTIVE đều đã được phân bổ (+15đ)
        UUID vaultMissingBeneficiary = vaults.stream()
                .map(Vault::getId)
                .filter(id -> assetsByVault.containsKey(id) && !vaultsWithBeneficiary.contains(id))
                .findFirst()
                .orElse(null);
        UUID vaultWithUnallocatedAsset = vaults.stream()
                .map(Vault::getId)
                .filter(id -> assetsByVault.getOrDefault(id, List.of()).stream()
                        .anyMatch(a -> !allocatedAssetIds.contains(a.getId())))
                .findFirst()
                .orElse(null);
        boolean allAllocated = !activeAssets.isEmpty()
                && vaultMissingBeneficiary == null
                && vaultWithUnallocatedAsset == null;
        UUID allocationFixVault = vaultMissingBeneficiary != null ? vaultMissingBeneficiary : vaultWithUnallocatedAsset;
        items.add(item("ASSET_BENEFICIARY_ALLOCATED", "assetBeneficiaryAllocated",
                "Allocate every active digital asset to at least one beneficiary",
                allAllocated, 15, allocationFixVault != null ? vaultUrl(allocationFixVault) : defaultVaultUrl));

        // 3. Vault có tài sản theo hồ sơ pháp lý (LEGAL_CLAIM_DOSSIER) đã đính kèm Di chúc (WILL) (+15đ)
        Set<UUID> vaultsWithLegalAssets = assetsByVault.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(OwnerReadinessServiceImpl::isLegalDossierAsset))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        UUID vaultNeedingWill = vaults.stream()
                .map(Vault::getId)
                .filter(id -> vaultsWithLegalAssets.contains(id) && !vaultsWithWill.contains(id))
                .findFirst()
                .orElse(null);
        // Chưa có tài sản pháp lý thì cần ít nhất 1 bản WILL ở bất kỳ vault nào
        boolean willSatisfied = !vaults.isEmpty() && (vaultsWithLegalAssets.isEmpty()
                ? !vaultsWithWill.isEmpty()
                : vaultNeedingWill == null);
        items.add(item("LEGAL_WILL_DOCUMENT", "legalWillDocument",
                "Upload a Legal Will (Di chúc) for banking & legal claim assets",
                willSatisfied, 15, vaultNeedingWill != null ? vaultUrl(vaultNeedingWill) : defaultVaultUrl));

        // 4. Đã chỉ định đủ Primary Executor và Backup Executor (+15đ)
        boolean hasPrimary = hasExecutor(executors, ExecutorType.PRIMARY);
        boolean hasBackup = hasExecutor(executors, ExecutorType.BACKUP);
        items.add(item("PRIMARY_AND_BACKUP_EXECUTOR", "executorsAssigned",
                "Assign both a Primary Executor and a Backup Executor",
                hasPrimary && hasBackup, 15, "/owner/executors"));

        // 5. Đã bật Dead Man's Switch (ACTIVE) (+15đ)
        boolean dmsActive = dmsOpt.map(d -> d.getStatus() == DmsStatus.ACTIVE).orElse(false);
        items.add(item("DMS_ACTIVE", "dmsActive",
                "Activate your Dead Man's Switch",
                dmsActive, 15, "/owner/dms"));

        // 6. Đã cấu hình cơ chế bảo vệ chống kích hoạt nhầm / dự phòng (+10đ)
        boolean safetyNet = dmsOpt.map(d -> d.getGracePeriodDays() != null && d.getGracePeriodDays() >= 3
                        && d.getReminderCount() != null && d.getReminderCount() >= 2)
                .orElse(false) && hasBackup;
        items.add(item("SAFETY_NET_CONFIGURED", "safetyNetConfigured",
                "Configure DMS multi-reminder grace period and backup protection",
                safetyNet, 10, "/owner/dms"));

        // 7. Đã rà soát/cập nhật hồ sơ trong vòng 6 tháng gần nhất (+10đ)
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        boolean reviewedRecently = isReviewedSince(vaults, activeAssets, dmsOpt.orElse(null), sixMonthsAgo);
        items.add(item("RECENT_REVIEW_6M", "recentReview",
                "Review or check in on your estate within the last 6 months",
                reviewedRecently, 10, dmsActive ? "/owner/dms" : defaultVaultUrl));

        int score = items.stream()
                .filter(ReadinessItemResponse::isDone)
                .mapToInt(ReadinessItemResponse::getPoints)
                .sum();
        int completedCount = (int) items.stream().filter(ReadinessItemResponse::isDone).count();

        return ReadinessResponse.builder()
                .score(score)
                .maxScore(items.stream().mapToInt(ReadinessItemResponse::getPoints).sum())
                .grade(computeGrade(score))
                .completedItems(completedCount)
                .totalItems(items.size())
                .items(items)
                .build();
    }

    // titleKey là khóa i18n trong namespace "readiness" của FE (frontend/src/i18n/locales/*/readiness.json)
    private static ReadinessItemResponse item(String key, String i18nKey, String title,
                                              boolean done, int points, String fixUrl) {
        return ReadinessItemResponse.builder()
                .key(key)
                .titleKey("items." + i18nKey)
                .title(title)
                .done(done)
                .points(points)
                .fixUrl(fixUrl)
                .build();
    }

    private static String vaultUrl(UUID vaultId) {
        return "/owner/vaults/" + vaultId;
    }

    private static boolean hasExecutor(List<ExecutorAssignment> executors, ExecutorType type) {
        return executors.stream()
                .anyMatch(e -> e.getExecutorType() == type && e.getStatus() != ExecutorStatus.REVOKED);
    }

    // N3: dùng cơ chế chuyển giao đã lưu, dữ liệu cũ (NULL) thì suy ra từ loại tài sản
    private static boolean isLegalDossierAsset(DigitalAsset asset) {
        return TransferMechanisms.resolve(asset.getTransferMechanism(), asset.getAssetType())
                == TransferMechanism.LEGAL_CLAIM_DOSSIER;
    }

    private static boolean isReviewedSince(
            List<Vault> vaults,
            List<DigitalAsset> assets,
            DmsConfig dmsConfig,
            LocalDateTime cutoff
    ) {
        if (dmsConfig != null && dmsConfig.getLastCheckinAt() != null
                && !dmsConfig.getLastCheckinAt().isBefore(cutoff)) {
            return true;
        }
        for (Vault v : vaults) {
            LocalDateTime ts = v.getUpdatedAt() != null ? v.getUpdatedAt() : v.getCreatedAt();
            if (ts != null && !ts.isBefore(cutoff)) {
                return true;
            }
        }
        for (DigitalAsset a : assets) {
            LocalDateTime ts = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            if (ts != null && !ts.isBefore(cutoff)) {
                return true;
            }
        }
        return false;
    }

    private static String computeGrade(int score) {
        if (score >= 85) return "EXCELLENT";
        if (score >= 65) return "GOOD";
        if (score >= 40) return "FAIR";
        return "AT_RISK";
    }
}
