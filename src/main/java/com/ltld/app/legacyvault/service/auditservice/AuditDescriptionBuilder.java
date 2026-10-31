package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * FR-05: chuyển AuditLog thành dòng nhật ký hiển thị cho Owner (câu mô tả tiếng Việt, che người làm và IP).
 * Dùng chung cho nhật ký Vault và nhật ký cấp Owner (DMS / Executor).
 */
@Component
public class AuditDescriptionBuilder {

    // FR-05 (Q42): hành động nội bộ của Admin, ẩn khỏi nhật ký Owner và nhật ký Vault
    public static final Set<AuditAction> OWNER_HIDDEN_ACTIONS = Collections.unmodifiableSet(
            EnumSet.of(AuditAction.ADMIN_VAULT_VIEWED, AuditAction.DMS_SUSPENDED, AuditAction.DMS_RESUMED,AuditAction.EXECUTOR_ASSIGNED_BY_ADMIN));

    // ownerId: chủ nhật ký đang xem (chủ Vault hoặc Owner gọi API), được thấy "Bạn" và IP đầy đủ.
    public VaultActivityLogResponse toResponse(AuditLog log, UUID ownerId) {
        return VaultActivityLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .actorName(maskActorName(log.getActor(), ownerId))
                .description(describe(log))
                .ipAddress(maskIpAddress(log.getActor(), ownerId, log.getIpAddress()))
                .createdAt(log.getCreatedAt())
                .build();
    }

    String maskActorName(User actor, UUID vaultOwnerId) {
        if (actor == null) return "System";
        if (actor.getId().equals(vaultOwnerId)) return "You";

        boolean isAdmin = false;
        boolean isExecutor = false;
        if (actor.getRoles() != null) {
            for (Role role : actor.getRoles()) {
                if ("ADMIN".equals(role.getCode())) isAdmin = true;
                if ("EXECUTOR".equals(role.getCode())) isExecutor = true;
            }
        }
        if (isAdmin) return "System administrator";
        if (isExecutor) return "Executor: " + actor.getFullName();
        return "Another user";
    }

    String maskIpAddress(User actor, UUID vaultOwnerId, String ipAddress) {
        if (ipAddress == null) return null;
        if (actor != null && actor.getId().equals(vaultOwnerId)) {
            return ipAddress; // Chủ két thấy IP đầy đủ
        }
        return "***.***.***.***"; // Che giấu IP nếu không phải chủ két
    }

    String describe(AuditLog log) {
        String detail = null;
        if (log.getMetadata() != null && log.getMetadata().containsKey("detail")) {
            detail = (String) log.getMetadata().get("detail");
        }
        return switch (log.getAction()) {
            case VAULT_CREATED -> "Created a new vault.";
            case VAULT_UPDATED -> "Updated vault details.";
            // FR-05: detail "name=<tên>" ghi từ deleteVault; log cũ detail null thì không có tên
            case VAULT_DELETED -> detail != null && detail.startsWith("name=")
                    ? "Deleted the vault \"" + detail.substring("name=".length()) + "\"."
                    : "Deleted the vault.";
            case VAULT_DELETE_DENIED -> "Vault deletion denied: " + (detail != null ? detail : "");
            case DOCUMENT_UPLOADED -> "Uploaded a new document.";
            case DOCUMENT_DELETED -> "Deleted a document.";
            case ADMIN_VAULT_VIEWED -> "An administrator viewed the vault details.";
            // FR-02: không nối detail vì detail là dạng kỹ thuật (id, trạng thái nội bộ)
            case BENEFICIARY_ASSIGNED -> "Assigned a beneficiary to the vault.";
            case BENEFICIARY_REPLACED -> "Changed the vault's beneficiary.";
            case BENEFICIARY_REMOVED -> "Removed the vault's beneficiary.";
            case BENEFICIARY_ASSIGN_DENIED -> "Beneficiary assignment denied.";
            case BENEFICIARY_REMOVE_DENIED -> "Beneficiary removal denied.";
            case BENEFICIARY_LINKED -> "The beneficiary linked their account to the vault.";
            case VAULT_UNLOCK -> "The vault was unlocked after legal verification.";
            case VAULT_ARCHIVED -> "The vault was archived because the claim period expired.";
            case CLAIM_COMPLETED -> "The beneficiary completed the handover.";
            case ASSET_VIEWED -> "Viewed digital asset details.";
            case ASSET_DOWNLOADED -> "Downloaded a digital asset.";
            // FR-27: Owner quản lý tài sản số; không nối detail
            case ASSET_CREATED -> "Added a new digital asset.";
            case ASSET_UPDATED -> "Updated a digital asset.";
            case ASSET_DELETED -> "Deleted a digital asset.";
            case DOCUMENT_DOWNLOADED -> isFailure(log) ? "Document download failed." : "Downloaded a document.";
            case LEGAL_VERIFICATION_REQUESTED -> "Submitted a legal verification request.";
            case LEGAL_REQUEST_REOPENED -> "The verification request missed its signing deadline and was returned to the review queue.";
            // FR-16: detail chứa email người nhận, không được hiển thị
            case BENEFICIARY_NOTIFIED -> "Sent the handover notice to the beneficiary.";
            case BENEFICIARY_NOTIFY_FAILED -> "Failed to notify the beneficiary.";
            case BENEFICIARY_NOTIFY_GAVE_UP -> "Stopped notifying the beneficiary after repeated failures.";
            case BENEFICIARY_PENDING_NOTIFIED -> "Sent a sign-up invitation to the beneficiary (no account yet).";
            case IDENTITY_OTP_SENT -> isFailure(log)
                    ? "Failed to send the beneficiary identity verification OTP."
                    : "Sent the beneficiary identity verification OTP.";
            case IDENTITY_VERIFIED -> "The beneficiary verified their identity.";
            case IDENTITY_VERIFY_FAILED -> "The beneficiary failed identity verification.";
            case IDENTITY_LOCKED -> "Identity verification locked after too many failed attempts.";
            // FR-03 / FR-06 / FR-07: nhật ký cấp Owner; detail chứa ownerId và id nội bộ nên không nối
            case DMS_CONFIGURED -> "Saved the Dead Man's Switch configuration.";
            case DMS_CHECKIN -> "Checked in to confirm still active.";
            case DMS_TRIGGERED -> "The Dead Man's Switch was triggered because the check-in deadline passed.";
            case DMS_TRIGGER_DEFERRED -> "Dead Man's Switch activation postponed 24 hours because the owner has not received any reminder.";
            case DMS_TRIGGERED_NO_EXECUTOR ->"The Dead Man's Switch was triggered but no executor is available.";
            // O5: detail chứa reason do Admin nhập nên không nối
            case DMS_SUSPENDED -> "An administrator suspended the Dead Man's Switch.";
            case DMS_RESUMED -> "An administrator resumed the Dead Man's Switch and started a new check-in cycle.";
            case EXECUTOR_ASSIGNED -> "Assigned an executor.";
            case EXECUTOR_REPLACED -> "Changed the executor.";
            case EXECUTOR_ASSIGNED_BY_ADMIN -> "An administrator assigned an executor because none was available.";
            // Owner thu hồi luôn ghi actor = Owner; BR-03 (FR-07 tự thu hồi Primary) ghi actor = null
            case EXECUTOR_REVOKE -> log.getActor() == null
                    ? "The system automatically revoked the primary executor for not acknowledging in time."
                    : "Revoked the executor.";
            case EXECUTOR_ACTIVATED -> "Activated the executor after the Dead Man's Switch was triggered.";
            case EXECUTOR_ACKNOWLEDGED -> "The executor acknowledged the activation notice.";
            default -> "Performed action: " + log.getAction();
        };
    }

    // AuditLogServiceImpl luôn ghi "result" (SUCCESS/FAILURE) vào metadata
    private boolean isFailure(AuditLog log) {
        return log.getMetadata() != null && "FAILURE".equals(log.getMetadata().get("result"));
    }
}
