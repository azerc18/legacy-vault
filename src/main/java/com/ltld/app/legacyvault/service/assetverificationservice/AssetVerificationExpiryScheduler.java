package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.AssetReverificationNeededEvent;
import com.ltld.app.legacyvault.repository.AssetVerificationRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.NotificationParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * C.5 / C.6: nhắc xác thực lại.
 * - Xác thực còn giá trị nhưng quá expires_at (6 tháng): lưu trạng thái EXPIRED, báo Owner trong app và phát event (mail N18).
 * - Sắp hết hạn (trong app.verification.reminder-days, mặc định 14 ngày): nhắc đúng một lần (reminded_at).
 * - Tài sản đã bị sửa sau khi xác thực (dấu vân tay đổi): lưu SELF_DECLARED (đúng với trạng thái hiệu lực), không nhắc.
 * Chỉ xét dòng xác thực MỚI NHẤT của tài sản ACTIVE trong Vault ACTIVE. Mỗi dòng một transaction riêng.
 * Mặc định BẬT; tắt bằng app.verification.expiry.enabled=false.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.verification.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class AssetVerificationExpiryScheduler {

    static final int BATCH_SIZE = 100;
    private static final String ENTITY_ASSET = "DigitalAsset";
    private static final List<AssetVerificationStatus> VERIFIED =
            List.of(AssetVerificationStatus.VERIFIED, AssetVerificationStatus.VERIFIED_SANDBOX);
    private static final String MSG_EXPIRED =
            "The verification of your asset \"%s\" has expired. Please verify it again so your executor and "
                    + "beneficiaries can rely on it.";
    private static final String MSG_SOON =
            "The verification of your asset \"%s\" expires on %s. Please verify it again.";

    private final AssetVerificationRepository verificationRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate requiresNewTx;
    private final int reminderDays;

    public AssetVerificationExpiryScheduler(AssetVerificationRepository verificationRepository,
                                            NotificationRepository notificationRepository,
                                            AuditLogService auditLogService,
                                            ApplicationEventPublisher eventPublisher,
                                            PlatformTransactionManager transactionManager,
                                            @Value("${app.verification.reminder-days:14}") int reminderDays) {
        this.verificationRepository = verificationRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.reminderDays = reminderDays;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Chạy mỗi 6 giờ; trễ 15 phút lần đầu để không chạy ngay lúc khởi động
    @Scheduled(fixedDelayString = "${app.verification.expiry-scan-delay:PT6H}",
            initialDelayString = "${app.verification.expiry-initial-delay:PT15M}")
    public void scan() {
        processDue(LocalDateTime.now());
    }

    public void processDue(LocalDateTime now) {
        LocalDateTime cutoff = now.plusDays(reminderDays);
        List<AssetVerification> due = verificationRepository.findDueForReverification(
                VERIFIED, cutoff, now, AssetStatus.ACTIVE, VaultStatus.ACTIVE, PageRequest.of(0, BATCH_SIZE));
        for (AssetVerification row : due) {
            UUID rowId = row.getId();
            try {
                requiresNewTx.executeWithoutResult(status -> process(rowId, now, cutoff, status));
            } catch (RuntimeException e) {
                log.error("C.5: could not process verification {}: {}", rowId, e.getMessage());
            }
        }
    }

    // Đọc lại dòng trong transaction riêng rồi kiểm lại điều kiện (có thể vừa được xác thực lại)
    private void process(UUID rowId, LocalDateTime now, LocalDateTime cutoff, TransactionStatus status) {
        AssetVerification v = verificationRepository.findById(rowId).orElse(null);
        if (v == null || !VERIFIED.contains(v.getStatus()) || v.getExpiresAt() == null) {
            status.setRollbackOnly();
            return;
        }
        DigitalAsset asset = v.getAsset();
        if (v.getFingerprint() == null || !v.getFingerprint().equals(AssetFingerprint.compute(asset))) {
            v.setStatus(AssetVerificationStatus.SELF_DECLARED);
            verificationRepository.save(v);
            return;
        }
        boolean expired = v.getExpiresAt().isBefore(now);
        if (!expired && (v.getRemindedAt() != null || !v.getExpiresAt().isBefore(cutoff))) {
            status.setRollbackOnly();
            return;
        }

        Vault vault = asset.getVault();
        User owner = vault.getOwner();
        if (expired) {
            v.setStatus(AssetVerificationStatus.EXPIRED);
        } else {
            v.setRemindedAt(now);
        }
        verificationRepository.save(v);

        String content = expired
                ? String.format(MSG_EXPIRED, asset.getAssetName())
                : String.format(MSG_SOON, asset.getAssetName(), v.getExpiresAt().toLocalDate());
        notificationRepository.save(Notification.builder()
                .recipient(owner)
                .type(NotificationType.ASSET_REVERIFY_NEEDED)
                .channel(NotificationChannel.IN_APP)
                .referenceType(ENTITY_ASSET)
                .referenceId(asset.getId())
                .content(content)
                .params(NotificationParams.json("asset", asset.getAssetName(), "vault", vault.getName(),
                        "vaultId", vault.getId(), "expiresAt", v.getExpiresAt(), "variant", expired ? "EXPIRED" : "SOON"))
                .status(NotificationStatus.SENT)
                .sentAt(now)
                .build());
        // Hệ thống tự chạy nên không có người thực hiện (userId null)
        auditLogService.log(expired ? AuditAction.ASSET_VERIFICATION_EXPIRED : AuditAction.ASSET_VERIFICATION_REMINDED,
                AuditResult.SUCCESS, null, null, vault.getId(), ENTITY_ASSET, asset.getId().toString(),
                v.getMethod() == null ? null : v.getMethod().name());
        eventPublisher.publishEvent(new AssetReverificationNeededEvent(owner.getId(), vault.getId(), asset.getId(),
                asset.getAssetName(), v.getExpiresAt(), expired));
    }
}
