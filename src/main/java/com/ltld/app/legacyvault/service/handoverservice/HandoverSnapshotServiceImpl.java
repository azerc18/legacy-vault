package com.ltld.app.legacyvault.service.handoverservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultHandoverSnapshot;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultHandoverSnapshotRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchema;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetSchemaRegistry;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.Sha256;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * C.6: bản chụp lúc bàn giao. Dữ liệu là JSON Lines (mỗi tài sản một dòng JSON phẳng, sắp theo id) để hash ổn định;
 * hash SHA-256 của toàn bộ dữ liệu được ghi vào audit log (HANDOVER_SNAPSHOT_CREATED).
 */
@Slf4j
@Service
public class HandoverSnapshotServiceImpl implements HandoverSnapshotService {

    private final VaultHandoverSnapshotRepository snapshotRepository;
    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final AssetVerificationService assetVerificationService;
    private final AuditLogService auditLogService;
    // REQUIRES_NEW: tạo và đọc lại trong cùng một transaction riêng (xem HandoverSnapshotService.ensureSnapshot)
    private final TransactionTemplate newTx;

    public HandoverSnapshotServiceImpl(VaultHandoverSnapshotRepository snapshotRepository,
                                       VaultRepository vaultRepository,
                                       DigitalAssetRepository digitalAssetRepository,
                                       AssetVerificationService assetVerificationService,
                                       AuditLogService auditLogService,
                                       PlatformTransactionManager transactionManager) {
        this.snapshotRepository = snapshotRepository;
        this.vaultRepository = vaultRepository;
        this.digitalAssetRepository = digitalAssetRepository;
        this.assetVerificationService = assetVerificationService;
        this.auditLogService = auditLogService;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void createForVaults(Collection<UUID> vaultIds) {
        if (vaultIds == null || vaultIds.isEmpty()) {
            return;
        }
        for (UUID vaultId : new LinkedHashSet<>(vaultIds)) {
            try {
                ensureSnapshot(vaultId);
            } catch (RuntimeException e) {
                log.warn("C.6: handover snapshot for vault {} failed: {}", vaultId, e.getMessage());
            }
        }
    }

    @Override
    public Optional<HandoverSnapshotView> ensureSnapshot(UUID vaultId) {
        try {
            return Optional.ofNullable(newTx.execute(status -> findOrCreate(vaultId)));
        } catch (DataIntegrityViolationException duplicate) {
            // Luồng khác vừa tạo bản chụp (uk_handover_snapshot_vault): đọc lại bản đó
            return Optional.ofNullable(newTx.execute(status ->
                    snapshotRepository.findByVaultId(vaultId).map(this::toView).orElse(null)));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyIntegrity(UUID vaultId) {
        return snapshotRepository.findByVaultId(vaultId)
                .map(s -> Sha256.hex(s.getSnapshotData()).equals(s.getSnapshotHash()))
                .orElse(false);
    }

    private HandoverSnapshotView findOrCreate(UUID vaultId) {
        Optional<VaultHandoverSnapshot> existing = snapshotRepository.findByVaultId(vaultId);
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        Vault vault = vaultRepository.findById(vaultId).orElse(null);
        // Chỉ chụp sau khi mở khóa; Vault đã lưu trữ thì nội dung đã bị xóa theo chính sách
        if (vault == null || (vault.getStatus() != VaultStatus.UNLOCKED && vault.getStatus() != VaultStatus.CLAIMED)) {
            return null;
        }
        List<DigitalAsset> assets = digitalAssetRepository.findByVaultIdAndStatus(vaultId, AssetStatus.ACTIVE);
        Map<UUID, AssetVerificationSummary> verification = assetVerificationService.summarize(assets);
        String data = buildData(assets, verification);
        String hash = Sha256.hex(data);

        VaultHandoverSnapshot saved = snapshotRepository.saveAndFlush(VaultHandoverSnapshot.builder()
                .vault(vault)
                .snapshotData(data)
                .snapshotHash(hash)
                .assetCount(assets.size())
                .build());
        // Hệ thống tự chụp nên không có người thực hiện (userId null)
        auditLogService.log(AuditAction.HANDOVER_SNAPSHOT_CREATED, AuditResult.SUCCESS, null, null,
                vaultId, "Vault", vaultId.toString(), "sha256=" + hash + ", assets=" + assets.size());
        return toView(saved);
    }

    /** JSON Lines, mỗi tài sản một dòng, sắp theo id: cùng dữ liệu thì cùng hash. */
    static String buildData(List<DigitalAsset> assets, Map<UUID, AssetVerificationSummary> verification) {
        StringBuilder sb = new StringBuilder();
        assets.stream().sorted(Comparator.comparing(DigitalAsset::getId)).forEach(a -> {
            AssetVerificationSummary v = verification == null ? null : verification.get(a.getId());
            Map<String, String> line = new LinkedHashMap<>();
            line.put("assetId", a.getId().toString());
            line.put("assetName", a.getAssetName() == null ? "" : a.getAssetName());
            line.put("assetType", String.valueOf(a.getAssetType()));
            line.put("transferMechanism", a.getTransferMechanism() == null ? "" : a.getTransferMechanism().name());
            line.put("publicFields", FlatJson.encode(publicFieldsOf(a)));
            line.put("verificationStatus", v == null ? AssetVerificationStatus.SELF_DECLARED.name() : v.status().name());
            line.put("verificationMethod", v == null || v.method() == null ? "" : v.method().name());
            line.put("verificationProvider", v == null || v.provider() == null ? "" : v.provider().name());
            line.put("verifiedAt", v == null || v.verifiedAt() == null ? "" : v.verifiedAt().toString());
            line.put("verificationExpiresAt", v == null || v.expiresAt() == null ? "" : v.expiresAt().toString());
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(FlatJson.encode(line));
        });
        return sb.toString();
    }

    // Trường công khai theo thứ tự khóa; trường nhạy cảm theo schema bị bỏ hẳn (không lưu cả giá trị lẫn hash)
    private static Map<String, String> publicFieldsOf(DigitalAsset asset) {
        Map<String, String> raw;
        try {
            raw = FlatJson.decode(asset.getPublicFieldsJson());
        } catch (IllegalArgumentException e) {
            return new TreeMap<>();
        }
        AssetSchema schema = AssetSchemaRegistry.schemaOf(asset.getAssetType());
        Set<String> sensitive = schema == null ? Set.of() : schema.fields().stream()
                .filter(AssetFieldSpec::sensitive)
                .map(AssetFieldSpec::key)
                .collect(Collectors.toSet());
        Map<String, String> out = new TreeMap<>();
        raw.forEach((k, v) -> { if (!sensitive.contains(k)) out.put(k, v); });
        return out;
    }

    private HandoverSnapshotView toView(VaultHandoverSnapshot s) {
        Map<UUID, AssetVerificationSummary> map = new LinkedHashMap<>();
        for (String line : s.getSnapshotData().split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, String> m = FlatJson.decode(line);
            map.put(UUID.fromString(m.get("assetId")), new AssetVerificationSummary(
                    AssetVerificationStatus.valueOf(m.get("verificationStatus")),
                    blank(m.get("verificationMethod")) ? null : AssetVerificationMethod.valueOf(m.get("verificationMethod")),
                    blank(m.get("verificationProvider")) ? null : VerificationProvider.valueOf(m.get("verificationProvider")),
                    blank(m.get("verifiedAt")) ? null : LocalDateTime.parse(m.get("verifiedAt")),
                    blank(m.get("verificationExpiresAt")) ? null : LocalDateTime.parse(m.get("verificationExpiresAt"))));
        }
        return new HandoverSnapshotView(s.getVault().getId(), s.getSnapshotHash(), s.getAssetCount(),
                s.getCreatedAt(), map);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
