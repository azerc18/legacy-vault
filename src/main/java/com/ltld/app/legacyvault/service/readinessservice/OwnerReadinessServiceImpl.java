package com.ltld.app.legacyvault.service.readinessservice;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessItemResponse;
import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerReadinessServiceImpl implements OwnerReadinessService {

    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public ReadinessResponse evaluateReadiness(UUID ownerId) {
        List<Vault> vaults = vaultRepository.findByOwnerIdWithBeneficiaryNewestFirst(ownerId);
        List<UUID> vaultIds = vaults.stream().map(Vault::getId).toList();

        List<DigitalAsset> activeAssets = vaultIds.isEmpty()
                ? List.of()
                : digitalAssetRepository.findByVaultIdInAndStatus(vaultIds, AssetStatus.ACTIVE);

        Map<UUID, List<DigitalAsset>> assetsByVault = activeAssets.stream()
                .collect(Collectors.groupingBy(a -> a.getVault().getId()));

        List<ExecutorAssignment> executors = executorAssignmentRepository.findAllByOwnerId(ownerId);
        Optional<DmsConfig> dmsOpt = dmsConfigRepository.findByOwnerId(ownerId);

        String defaultVaultUrl = vaults.isEmpty()
                ? "/owner/vaults/new"
                : "/owner/vaults/" + vaults.get(0).getId();

        List<ReadinessItemResponse> items = new ArrayList<>();

        // 1. Đã tạo Master Vault và có ít nhất 1 tài sản số (+20đ)
        boolean hasVaultAndAsset = !vaults.isEmpty() && !activeAssets.isEmpty();
        items.add(ReadinessItemResponse.builder()
                .key("MASTER_VAULT_AND_ASSET")
                .titleKey("readiness.items.masterVaultAndAsset")
                .title("Create Master Estate Vault and add at least 1 digital asset")
                .done(hasVaultAndAsset)
                .points(20)
                .fixUrl(defaultVaultUrl)
                .build());

        // 2. Mọi tài sản đều đã được phân bổ người thụ hưởng rõ ràng (+15đ)
        Vault vaultMissingBeneficiary = vaults.stream()
                .filter(v -> assetsByVault.containsKey(v.getId()) && !hasBeneficiaryAssigned(v))
                .findFirst()
                .orElse(null);
        boolean allAllocated = !activeAssets.isEmpty() && vaultMissingBeneficiary == null
                && vaults.stream().anyMatch(this::hasBeneficiaryAssigned);
        String allocationFixUrl = vaultMissingBeneficiary != null
                ? "/owner/vaults/" + vaultMissingBeneficiary.getId()
                : defaultVaultUrl;
        items.add(ReadinessItemResponse.builder()
                .key("ASSET_BENEFICIARY_ALLOCATED")
                .titleKey("readiness.items.assetBeneficiaryAllocated")
                .title("Assign beneficiaries to all vaults containing digital assets")
                .done(allAllocated)
                .points(15)
                .fixUrl(allocationFixUrl)
                .build());

        // 3. Tài sản nhóm Ngân hàng/Pháp lý đã được gắn kèm bản Di chúc (LegalDocument WILL) (+15đ)
        boolean willSatisfied = checkLegalWillRequirement(vaults, assetsByVault);
        String willFixUrl = findVaultNeedingWillUrl(vaults, assetsByVault, defaultVaultUrl);
        items.add(ReadinessItemResponse.builder()
                .key("LEGAL_WILL_DOCUMENT")
                .titleKey("readiness.items.legalWillDocument")
                .title("Upload a Legal Will (Di chúc) for banking & legal claim assets")
                .done(willSatisfied)
                .points(15)
                .fixUrl(willFixUrl)
                .build());

        // 4. Đã chỉ định đủ Primary Executor và Backup Executor (+15đ)
        boolean hasPrimary = executors.stream()
                .anyMatch(e -> e.getExecutorType() == ExecutorType.PRIMARY && e.getStatus() != ExecutorStatus.REVOKED);
        boolean hasBackup = executors.stream()
                .anyMatch(e -> e.getExecutorType() == ExecutorType.BACKUP && e.getStatus() != ExecutorStatus.REVOKED);
        boolean bothExecutors = hasPrimary && hasBackup;
        items.add(ReadinessItemResponse.builder()
                .key("PRIMARY_AND_BACKUP_EXECUTOR")
                .titleKey("readiness.items.executorsAssigned")
                .title("Assign both a Primary Executor and a Backup Executor")
                .done(bothExecutors)
                .points(15)
                .fixUrl("/owner/executors")
                .build());

        // 5. Đã bật Dead Man's Switch (ACTIVE) (+15đ)
        boolean dmsActive = dmsOpt.map(d -> d.getStatus() == DmsStatus.ACTIVE).orElse(false);
        items.add(ReadinessItemResponse.builder()
                .key("DMS_ACTIVE")
                .titleKey("readiness.items.dmsActive")
                .title("Activate your Dead Man's Switch")
                .done(dmsActive)
                .points(15)
                .fixUrl("/owner/dms")
                .build());

        // 6. Đã cấu hình cơ chế bảo vệ chống kích hoạt nhầm / dự phòng (+10đ)
        boolean safetyNet = dmsOpt.map(d -> d.getGracePeriodDays() != null && d.getGracePeriodDays() >= 3
                        && d.getReminderCount() != null && d.getReminderCount() >= 2)
                .orElse(false) && hasBackup;
        items.add(ReadinessItemResponse.builder()
                .key("SAFETY_NET_CONFIGURED")
                .titleKey("readiness.items.safetyNetConfigured")
                .title("Configure DMS multi-reminder grace period and backup protection")
                .done(safetyNet)
                .points(10)
                .fixUrl("/owner/dms")
                .build());

        // 7. Đã rà soát/cập nhật hồ sơ trong vòng 6 tháng gần nhất (+10đ)
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        boolean reviewedRecently = isReviewedSince(vaults, activeAssets, dmsOpt.orElse(null), sixMonthsAgo);
        items.add(ReadinessItemResponse.builder()
                .key("RECENT_REVIEW_6M")
                .titleKey("readiness.items.recentReview")
                .title("Review or check in on your estate within the last 6 months")
                .done(reviewedRecently)
                .points(10)
                .fixUrl(dmsActive ? "/owner/dms" : defaultVaultUrl)
                .build());

        int score = items.stream()
                .filter(ReadinessItemResponse::isDone)
                .mapToInt(ReadinessItemResponse::getPoints)
                .sum();
        int completedCount = (int) items.stream().filter(ReadinessItemResponse::isDone).count();

        return ReadinessResponse.builder()
                .score(score)
                .maxScore(100)
                .grade(computeGrade(score))
                .completedItems(completedCount)
                .totalItems(items.size())
                .items(items)
                .build();
    }

    private boolean hasBeneficiaryAssigned(Vault v) {
        return v.getBeneficiary() != null
                || (v.getBeneficiaryEmail() != null && !v.getBeneficiaryEmail().isBlank());
    }

    private boolean checkLegalWillRequirement(List<Vault> vaults, Map<UUID, List<DigitalAsset>> assetsByVault) {
        if (vaults.isEmpty()) {
            return false;
        }
        Set<UUID> vaultsWithLegalAssets = assetsByVault.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(this::isLegalDossierAsset))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!vaultsWithLegalAssets.isEmpty()) {
            for (UUID vaultId : vaultsWithLegalAssets) {
                if (!vaultHasWillDocument(vaultId)) {
                    return false;
                }
            }
            return true;
        }

        // Nếu chưa có tài sản BANK_ACCOUNT/DOCUMENT riêng thì cần ít nhất 1 bản WILL trong bất kỳ Vault nào
        return vaults.stream().anyMatch(v -> vaultHasWillDocument(v.getId()));
    }

    private String findVaultNeedingWillUrl(
            List<Vault> vaults,
            Map<UUID, List<DigitalAsset>> assetsByVault,
            String fallbackUrl
    ) {
        for (Vault v : vaults) {
            List<DigitalAsset> vAssets = assetsByVault.getOrDefault(v.getId(), List.of());
            if (vAssets.stream().anyMatch(this::isLegalDossierAsset) && !vaultHasWillDocument(v.getId())) {
                return "/owner/vaults/" + v.getId();
            }
        }
        return fallbackUrl;
    }

    private boolean isLegalDossierAsset(DigitalAsset asset) {
        return asset.getAssetType() == AssetType.BANK_ACCOUNT || asset.getAssetType() == AssetType.DOCUMENT;
    }

    private boolean vaultHasWillDocument(UUID vaultId) {
        List<LegalDocument> docs = legalDocumentRepository.findByVaultId(vaultId);
        return docs != null && docs.stream().anyMatch(d -> d.getDocumentType() == DocumentType.WILL);
    }

    private boolean isReviewedSince(
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

