package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.dto.assetverificationdto.ConfirmVerificationRequest;
import com.ltld.app.legacyvault.dto.assetverificationdto.StartVerificationRequest;
import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.TooManyAttemptsException;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetVerificationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C.5: lớp xác thực tài sản.
 * - Mỗi lần xác thực là một dòng asset_verifications (lấy dòng mới nhất làm trạng thái).
 * - Trạng thái HIỆU LỰC được tính khi đọc: dấu vân tay đổi => SELF_DECLARED; quá hạn => EXPIRED.
 * - Mỗi thử thách chỉ được thử đúng 1 lần: sai thì lưu FAILED và Owner phải start lại (chống dò mã).
 */
@Service
@RequiredArgsConstructor
public class AssetVerificationServiceImpl implements AssetVerificationService {

    /** Xác thực thành công có hiệu lực 6 tháng, sau đó thành EXPIRED. */
    static final int VALIDITY_DAYS = 180;
    /** Giới hạn số lần start mỗi tài sản mỗi phút (chống spam mail OTP). */
    static final int MAX_STARTS_PER_MINUTE = 5;
    private static final int MAX_IN_MEMORY_KEYS = 500;

    private final Map<UUID, Deque<Long>> startBuckets = new ConcurrentHashMap<>();

    private static final AssetVerificationSummary SELF_DECLARED_SUMMARY =
            new AssetVerificationSummary(AssetVerificationStatus.SELF_DECLARED, null, null, null, null);

    private final AssetVerificationRepository verificationRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final VaultRepository vaultRepository;
    // ObjectProvider để app vẫn khởi động được khi chưa có verifier nào
    private final ObjectProvider<AssetVerifier> verifiers;
    private final AuditLogService auditLogService;
    private final VaultModificationGuard vaultModificationGuard;

    /** false => từ chối kết quả của verifier mô phỏng (SANDBOX). */
    @Value("${app.verification.sandbox.enabled:true}")
    private boolean sandboxEnabled;

    // =========================================================
    // Đọc trạng thái
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public AssetVerificationResponse getStatus(UUID ownerId, UUID vaultId, UUID assetId) {
        loadOwnedVault(vaultId, ownerId);
        DigitalAsset asset = loadActiveAsset(assetId, vaultId);
        AssetVerification latest = verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId).orElse(null);
        return toResponse(asset, summaryOf(asset, latest, LocalDateTime.now()), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AssetVerificationSummary> summarize(Collection<DigitalAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = assets.stream().map(DigitalAsset::getId).toList();
        Map<UUID, AssetVerification> latest = verificationRepository.findLatestByAssetIds(ids).stream()
                // Nếu 2 dòng trùng createdAt thì lấy dòng bất kỳ (hiếm, chấp nhận được)
                .collect(Collectors.toMap(v -> v.getAsset().getId(), Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, AssetVerificationSummary> result = new LinkedHashMap<>();
        for (DigitalAsset asset : assets) {
            result.put(asset.getId(), summaryOf(asset, latest.get(asset.getId()), now));
        }
        return result;
    }

    @Override
    public Map<AssetType, List<AssetVerificationMethod>> methodsByType() {
        Map<AssetType, List<AssetVerificationMethod>> result = new EnumMap<>(AssetType.class);
        verifiers.orderedStream().forEach(v ->
                result.computeIfAbsent(v.type(), k -> new ArrayList<>()).addAll(v.methods()));
        return result;
    }

    // =========================================================
    // Bắt đầu xác thực
    // =========================================================

    @Override
    @Transactional
    public AssetVerificationResponse start(UUID ownerId, UUID vaultId, UUID assetId,
                                           StartVerificationRequest request) {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);
        DigitalAsset asset = loadActiveAsset(assetId, vaultId);
        AssetVerificationMethod method = request.getMethod();

        LocalDateTime now = LocalDateTime.now();
        AssetVerification latest = verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId).orElse(null);
        ensureNotAlreadyVerified(asset, latest, now);
        AssetVerifier verifier = findVerifier(asset, method);

        if (!allowStart(assetId)) {
            throw new TooManyAttemptsException("Too many verification requests, please try again in a minute");
        }

        AssetVerifier.StartResult result = verifier.start(asset, method, ownerId);
        VerificationProvider provider = providerOf(result.provider());
        requireSandboxAllowed(provider);

        AssetVerification row = verificationRepository.save(AssetVerification.builder()
                .asset(asset)
                .method(method)
                .provider(provider)
                .status(AssetVerificationStatus.PENDING)
                .fingerprint(AssetFingerprint.compute(asset))
                .challengeHash(result.challengeHash())
                .expiresAt(result.challengeExpiresAt())
                .build());

        audit(AuditAction.ASSET_VERIFICATION_STARTED, AuditResult.SUCCESS, vault, ownerId, assetId, method);
        return toResponse(asset, summaryOfRow(row), result.message(), result.clientData());
    }

    // =========================================================
    // Hoàn tất xác thực
    // =========================================================

    @Override
    @Transactional
    public AssetVerificationResponse confirm(UUID ownerId, UUID vaultId, UUID assetId,
                                             ConfirmVerificationRequest request) {
        Vault vault = loadOwnedVault(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);
        DigitalAsset asset = loadActiveAsset(assetId, vaultId);
        AssetVerificationMethod method = request.getMethod();

        LocalDateTime now = LocalDateTime.now();
        AssetVerification latest = verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId).orElse(null);
        ensureNotAlreadyVerified(asset, latest, now);
        AssetVerifier verifier = findVerifier(asset, method);

        String fingerprint = AssetFingerprint.compute(asset);
        AssetVerification pending = (latest != null
                && latest.getStatus() == AssetVerificationStatus.PENDING
                && latest.getMethod() == method) ? latest : null;
        if (pending != null) {
            if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(now)) {
                throw new VaultException("The verification challenge has expired, please start again",
                        HttpStatus.BAD_REQUEST);
            }
            if (!fingerprint.equals(pending.getFingerprint())) {
                throw new VaultException("The asset changed since verification started, please start again",
                        HttpStatus.BAD_REQUEST);
            }
        }

        Map<String, String> payload = request.getPayload() == null ? Map.of() : request.getPayload();
        AssetVerifier.VerifyResult result = verifier.confirm(asset, method, payload, ownerId,
                pending == null ? null : pending.getChallengeHash());
        VerificationProvider provider = providerOf(result.provider());
        requireSandboxAllowed(provider);

        AssetVerification row = pending != null ? pending
                : AssetVerification.builder().asset(asset).method(method).build();
        row.setProvider(provider);
        row.setFingerprint(fingerprint);
        row.setChallengeHash(null);
        if (result.success()) {
            row.setStatus(provider == VerificationProvider.REAL
                    ? AssetVerificationStatus.VERIFIED : AssetVerificationStatus.VERIFIED_SANDBOX);
            row.setVerifiedAt(now);
            row.setExpiresAt(now.plusDays(VALIDITY_DAYS));
            Map<String, String> evidence = result.evidence();
            row.setEvidenceJson(evidence == null || evidence.isEmpty() ? null : FlatJson.encode(evidence));
        } else {
            row.setStatus(AssetVerificationStatus.FAILED);
            row.setVerifiedAt(null);
            row.setExpiresAt(null);
        }
        row = verificationRepository.save(row);

        audit(result.success() ? AuditAction.ASSET_VERIFICATION_VERIFIED : AuditAction.ASSET_VERIFICATION_FAILED,
                result.success() ? AuditResult.SUCCESS : AuditResult.FAILURE, vault, ownerId, assetId, method);
        return toResponse(asset, summaryOfRow(row), result.message(), null);
    }

    // =========================================================
    // Helpers
    // =========================================================

    boolean allowStart(UUID assetId) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        if (startBuckets.size() >= MAX_IN_MEMORY_KEYS) {
            pruneStaleBuckets(windowStart);
        }
        Deque<Long> timestamps = startBuckets.computeIfAbsent(assetId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_STARTS_PER_MINUTE) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private void pruneStaleBuckets(long windowStart) {
        startBuckets.entrySet().removeIf(e -> {
            Deque<Long> timestamps = e.getValue();
            synchronized (timestamps) {
                Long last = timestamps.peekLast();
                return last == null || last < windowStart;
            }
        });
    }

    private static boolean isVerified(AssetVerificationStatus status) {
        return status == AssetVerificationStatus.VERIFIED || status == AssetVerificationStatus.VERIFIED_SANDBOX;
    }

    /** Trạng thái hiệu lực: đã xác thực nhưng dấu vân tay đổi => SELF_DECLARED; quá hạn => EXPIRED. */
    private static AssetVerificationStatus effectiveStatus(DigitalAsset asset, AssetVerification row,
                                                           LocalDateTime now) {
        AssetVerificationStatus status = row.getStatus();
        if (isVerified(status)) {
            if (row.getFingerprint() == null || !row.getFingerprint().equals(AssetFingerprint.compute(asset))) {
                return AssetVerificationStatus.SELF_DECLARED;
            }
            if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(now)) {
                return AssetVerificationStatus.EXPIRED;
            }
        }
        return status;
    }

    private static AssetVerificationSummary summaryOf(DigitalAsset asset, AssetVerification row, LocalDateTime now) {
        if (row == null) {
            return SELF_DECLARED_SUMMARY;
        }
        AssetVerificationStatus status = effectiveStatus(asset, row, now);
        if (status == AssetVerificationStatus.SELF_DECLARED) {
            return SELF_DECLARED_SUMMARY;
        }
        return new AssetVerificationSummary(status, row.getMethod(), row.getProvider(),
                row.getVerifiedAt(), row.getExpiresAt());
    }

    private static AssetVerificationSummary summaryOfRow(AssetVerification row) {
        return new AssetVerificationSummary(row.getStatus(), row.getMethod(), row.getProvider(),
                row.getVerifiedAt(), row.getExpiresAt());
    }

    private void ensureNotAlreadyVerified(DigitalAsset asset, AssetVerification latest, LocalDateTime now) {
        if (latest != null && isVerified(effectiveStatus(asset, latest, now))) {
            throw new VaultException("Asset is already verified", HttpStatus.CONFLICT);
        }
    }

    private AssetVerifier findVerifier(DigitalAsset asset, AssetVerificationMethod method) {
        return verifiers.orderedStream()
                .filter(v -> v.type() == asset.getAssetType() && v.methods().contains(method))
                .findFirst()
                .orElseThrow(() -> new VaultException(
                        "Verification method is not supported for this asset type", HttpStatus.BAD_REQUEST));
    }

    private List<AssetVerificationMethod> availableMethods(DigitalAsset asset) {
        return verifiers.orderedStream()
                .filter(v -> v.type() == asset.getAssetType())
                .flatMap(v -> v.methods().stream())
                .toList();
    }

    /** Verifier quên trả provider thì coi là mô phỏng (an toàn hơn là nhận nhầm là xác thực thật). */
    private static VerificationProvider providerOf(VerificationProvider provider) {
        return provider != null ? provider : VerificationProvider.SANDBOX;
    }

    private void requireSandboxAllowed(VerificationProvider provider) {
        if (provider == VerificationProvider.SANDBOX && !sandboxEnabled) {
            throw new VaultException("Sandbox verification is disabled", HttpStatus.BAD_REQUEST);
        }
    }

    private void audit(AuditAction action, AuditResult result, Vault vault, UUID ownerId, UUID assetId,
                       AssetVerificationMethod method) {
        String email = vault.getOwner() != null ? vault.getOwner().getEmail() : null;
        auditLogService.log(action, result, ownerId, email, vault.getId(), "DigitalAsset",
                assetId.toString(), method.name());
    }

    private AssetVerificationResponse toResponse(DigitalAsset asset, AssetVerificationSummary summary,
                                                 String message, Map<String, String> clientData) {
        return AssetVerificationResponse.builder()
                .assetId(asset.getId())
                .status(summary.status())
                .method(summary.method())
                .provider(summary.provider())
                .verifiedAt(summary.verifiedAt())
                .expiresAt(summary.expiresAt())
                .availableMethods(availableMethods(asset))
                .message(message)
                .clientData(clientData)
                .build();
    }

    // Sai chủ => 404 (không lộ vault có tồn tại), giống DigitalAssetServiceImpl
    private Vault loadOwnedVault(UUID vaultId, UUID ownerId) {
        return vaultRepository.findById(vaultId)
                .filter(v -> v.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));
    }

    // Tài sản sai vault hoặc không ACTIVE => 404
    private DigitalAsset loadActiveAsset(UUID assetId, UUID vaultId) {
        return digitalAssetRepository.findById(assetId)
                .filter(a -> a.getStatus() == AssetStatus.ACTIVE && a.getVault().getId().equals(vaultId))
                .orElseThrow(() -> new VaultException("Asset not found", HttpStatus.NOT_FOUND));
    }
}
