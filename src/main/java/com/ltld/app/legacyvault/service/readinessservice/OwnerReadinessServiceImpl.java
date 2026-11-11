package com.ltld.app.legacyvault.service.readinessservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.dto.readinessdto.ReadinessItemResponse;
import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.*;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import com.ltld.app.legacyvault.utility.TransferMechanisms;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    // Ngưỡng điểm tối thiểu của từng hạng (dưới FAIR_MIN là AT_RISK)
    static final int EXCELLENT_MIN = 85;
    static final int GOOD_MIN = 65;
    static final int FAIR_MIN = 40;
    // Cùng giá trị với AssetVerificationExpiryScheduler để thẻ Readiness khớp với thông báo nhắc
    @Value("${app.verification.reminder-days:14}")
    private int reverifySoonDays = 14;

    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final AssetAllocationRepository assetAllocationRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsConfigRepository dmsConfigRepository;
    private final AssetVerificationService assetVerificationService;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ReadinessResponse evaluateReadiness(UUID ownerId) {
        List<Vault> vaults = vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        List<UUID> vaultIds = vaults.stream().map(Vault::getId).toList();

        List<DigitalAsset> activeAssets;
        Set<UUID> vaultsWithBeneficiary;
        Set<UUID> allocatedAssetIds;
        Set<UUID> vaultsWithWill;
        boolean hasContingent;
        if (vaultIds.isEmpty()) {
            activeAssets = List.of();
            vaultsWithBeneficiary = Set.of();
            allocatedAssetIds = Set.of();
            vaultsWithWill = Set.of();
            hasContingent = false;
        } else {
            activeAssets = digitalAssetRepository.findByVaultIdInAndStatus(vaultIds, AssetStatus.ACTIVE);
            vaultsWithBeneficiary = new HashSet<>(vaultBeneficiaryRepository.findVaultIdsWithBeneficiaryByOwnerId(ownerId));
            allocatedAssetIds = activeAssets.isEmpty()
                    ? Set.of()
                    : new HashSet<>(assetAllocationRepository.findAllocatedActiveAssetIdsByOwnerId(ownerId));
            vaultsWithWill = new HashSet<>(
                    legalDocumentRepository.findVaultIdsWithDocumentTypeByOwnerId(ownerId, DocumentType.WILL));
            hasContingent = !activeAssets.isEmpty() && assetAllocationRepository.existsContingentForOwner(ownerId);
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
        // n1: phân bổ tường minh đã kéo theo vault có người thụ hưởng, nhưng vẫn tìm riêng vault thiếu người
        // để khi bật lại fixUrl thì ưu tiên đưa Owner tới vault cần thêm người trước khi phân bổ.
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
        // m1: FE chưa có giao diện phân bổ nên chưa có trang để sửa -> fixUrl = null (FE ẩn nút "Fix now").
        // TODO(N2-owner-FE): bật lại fixUrl khi N2-owner-FE merge:
        //   allocationFixVault != null ? vaultUrl(allocationFixVault) : defaultVaultUrl,
        //   với allocationFixVault = vaultMissingBeneficiary != null ? vaultMissingBeneficiary : vaultWithUnallocatedAsset
        items.add(item("ASSET_BENEFICIARY_ALLOCATED", "assetBeneficiaryAllocated",
                "Choose who receives each digital asset",
                allAllocated, 15, null));

        // 3. Vault có tài sản theo hồ sơ pháp lý (LEGAL_CLAIM_DOSSIER) đã đính kèm Di chúc (WILL) (+15đ)
        // TODO(N3b): khi có UI linkedDocumentGroupId thì đổi thành "mọi tài sản LEGAL_CLAIM_DOSSIER có
        //   linkedDocumentGroupId trỏ tới nhóm WILL còn hiệu lực" (hiện chấm theo "vault có tài liệu WILL").
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
                "Upload a Legal Will for banking & legal claim assets",
                willSatisfied, 15, vaultNeedingWill != null ? vaultUrl(vaultNeedingWill) : defaultVaultUrl));

        // 4. Đã chỉ định đủ Primary Executor và Backup Executor (+15đ)
        boolean hasPrimary = hasExecutor(executors, ExecutorType.PRIMARY);
        boolean hasBackup = hasExecutor(executors, ExecutorType.BACKUP);
        items.add(item("PRIMARY_AND_BACKUP_EXECUTOR", "executorsAssigned",
                "Assign both a Primary Executor and a Backup Executor",
                hasPrimary && hasBackup, 15, "/owner/executors"));

        // KYC: xác minh danh tính là điều kiện bật DMS lần đầu. 0 điểm (điểm đã tính ở tiêu chí DMS), chỉ để nhắc Owner.
        boolean identityVerified = userRepository.findById(ownerId)
                .map(u -> u.getKycLevel() != null && u.getKycLevel() != KycLevel.NONE)
                .orElse(false);
        items.add(item("IDENTITY_VERIFIED", "identityVerified",
                "Verify your identity to enable the Dead Man's Switch",
                identityVerified, 0, "/owner/identity"));

        // 5. Đã bật Dead Man's Switch (ACTIVE) (+15đ)
        boolean dmsActive = dmsOpt.map(d -> d.getStatus() == DmsStatus.ACTIVE).orElse(false);
        items.add(item("DMS_ACTIVE", "dmsActive",
                "Activate your Dead Man's Switch",
                dmsActive, 15, "/owner/dms"));

        // 6. Lưới an toàn theo đặc tả (+10đ): có ít nhất 1 tài sản ACTIVE được chỉ định người thụ hưởng dự phòng
        // (asset_allocations.contingent_beneficiary_id khác null). Backup Executor đã chấm ở tiêu chí 4, không trừ hai lần.
        // X2: thêm || có DMS confirmer khi X2 merge
        boolean safetyNet = hasContingent;
        // FE chưa có giao diện chọn người dự phòng / người xác nhận chéo -> fixUrl = null (FE ẩn nút "Fix now").
        // TODO(N2-owner-FE / X2): đặt fixUrl khi có trang tương ứng.
        items.add(item("SAFETY_NET_CONFIGURED", "safetyNetConfigured",
                "Add a contingent beneficiary or DMS trusted confirmers",
                safetyNet, 10, null));

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

        // PR 2: tài sản cần xác thực lại (không ảnh hưởng điểm số)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soonCutoff = now.plusDays(reverifySoonDays);
        List<DigitalAsset> liveAssets = activeAssets.stream()
                .filter(a -> a.getVault().getStatus() == VaultStatus.ACTIVE)
                .toList();
        int needReverification = 0;
        int expiringSoon = 0;
        if (!liveAssets.isEmpty()) {
            for (AssetVerificationSummary s : assetVerificationService.summarize(liveAssets).values()) {
                if (s == null) {
                    continue;
                }
                if (s.status() == AssetVerificationStatus.EXPIRED) {
                    needReverification++;
                } else if ((s.status() == AssetVerificationStatus.VERIFIED
                        || s.status() == AssetVerificationStatus.VERIFIED_SANDBOX)
                        && s.expiresAt() != null && s.expiresAt().isBefore(soonCutoff)) {
                    expiringSoon++;
                }
            }
        }

        return ReadinessResponse.builder()
                .score(score)
                .maxScore(items.stream().mapToInt(ReadinessItemResponse::getPoints).sum())
                .grade(computeGrade(score))
                .completedItems(completedCount)
                .totalItems(items.size())
                .items(items)
                .assetsNeedReverification(needReverification)
                .assetsExpiringSoon(expiringSoon)
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

    // m3 (N3 đã merge): dùng cơ chế chuyển giao đã lưu trên tài sản; chỉ dữ liệu cũ (NULL) mới suy ra từ loại tài sản
    static boolean isLegalDossierAsset(DigitalAsset asset) {
        TransferMechanism stored = asset.getTransferMechanism();
        TransferMechanism mechanism = stored != null
                ? stored
                : TransferMechanisms.resolve(null, asset.getAssetType());
        return mechanism == TransferMechanism.LEGAL_CLAIM_DOSSIER;
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
        if (score >= EXCELLENT_MIN) return "EXCELLENT";
        if (score >= GOOD_MIN) return "GOOD";
        if (score >= FAIR_MIN) return "FAIR";
        return "AT_RISK";
    }
}
