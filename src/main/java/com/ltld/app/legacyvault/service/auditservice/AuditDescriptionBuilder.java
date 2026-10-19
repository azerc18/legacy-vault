package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FR-05: chuyển AuditLog thành dòng nhật ký hiển thị cho Owner (câu mô tả tiếng Việt, che người làm và IP).
 * Dùng chung cho nhật ký Vault và nhật ký cấp Owner (DMS / Executor).
 */
@Component
public class AuditDescriptionBuilder {

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
        if (actor == null) return "Hệ thống";
        if (actor.getId().equals(vaultOwnerId)) return "Bạn";

        boolean isAdmin = false;
        boolean isExecutor = false;
        if (actor.getRoles() != null) {
            for (Role role : actor.getRoles()) {
                if ("ADMIN".equals(role.getCode())) isAdmin = true;
                if ("EXECUTOR".equals(role.getCode())) isExecutor = true;
            }
        }
        if (isAdmin) return "Quản trị viên hệ thống";
        if (isExecutor) return "Người thi hành: " + actor.getFullName();
        return "Người dùng khác";
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
            case VAULT_CREATED -> "Khởi tạo két sắt mới.";
            case VAULT_UPDATED -> "Cập nhật thông tin két sắt.";
            case VAULT_DELETED -> "Xóa két sắt thành công.";
            case VAULT_DELETE_DENIED -> "Từ chối xóa két sắt: " + (detail != null ? detail : "");
            case DOCUMENT_UPLOADED -> "Tải lên tài liệu mới thành công.";
            case DOCUMENT_DELETED -> "Xóa tài liệu thành công.";
            case ADMIN_VAULT_VIEWED -> "Quản trị viên xem chi tiết két sắt.";
            // FR-02: không nối detail vì detail là dạng kỹ thuật (id, trạng thái nội bộ)
            case BENEFICIARY_ASSIGNED -> "Chỉ định người thụ hưởng cho két sắt.";
            case BENEFICIARY_REPLACED -> "Thay đổi người thụ hưởng của két sắt.";
            case BENEFICIARY_ASSIGN_DENIED -> "Từ chối chỉ định người thụ hưởng.";
            case BENEFICIARY_LINKED -> "Người thụ hưởng đã liên kết tài khoản với két sắt.";
            case VAULT_UNLOCK -> "Két sắt được mở khóa sau khi xác minh pháp lý.";
            case VAULT_ARCHIVED -> "Két sắt được lưu trữ do hết hạn nhận bàn giao.";
            case CLAIM_COMPLETED -> "Người thụ hưởng đã hoàn tất nhận bàn giao.";
            case ASSET_VIEWED -> "Xem chi tiết tài sản số.";
            case ASSET_DOWNLOADED -> "Tải xuống tài sản số.";
            case DOCUMENT_DOWNLOADED -> isFailure(log) ? "Tải xuống tài liệu thất bại." : "Tải xuống tài liệu.";
            case LEGAL_VERIFICATION_REQUESTED -> "Gửi yêu cầu xác minh pháp lý.";
            // FR-16: detail chứa email người nhận, không được hiển thị
            case BENEFICIARY_NOTIFIED -> "Đã gửi thông báo bàn giao cho người thụ hưởng.";
            case BENEFICIARY_NOTIFY_FAILED -> "Gửi thông báo cho người thụ hưởng thất bại.";
            case BENEFICIARY_NOTIFY_GAVE_UP -> "Ngừng gửi thông báo cho người thụ hưởng sau nhiều lần thất bại.";
            case BENEFICIARY_PENDING_NOTIFIED -> "Đã gửi lời mời đăng ký tới người thụ hưởng (chưa có tài khoản).";
            case IDENTITY_OTP_SENT -> "Gửi mã OTP xác minh danh tính người thụ hưởng.";
            case IDENTITY_VERIFIED -> "Người thụ hưởng xác minh danh tính thành công.";
            case IDENTITY_VERIFY_FAILED -> "Người thụ hưởng xác minh danh tính thất bại.";
            case IDENTITY_LOCKED -> "Tạm khóa xác minh danh tính do nhập sai quá số lần cho phép.";
            // FR-03 / FR-06 / FR-07: nhật ký cấp Owner; detail chứa ownerId và id nội bộ nên không nối
            case DMS_CONFIGURED -> "Lưu cấu hình Dead Man's Switch.";
            case DMS_CHECKIN -> "Check-in xác nhận vẫn còn hoạt động.";
            case DMS_TRIGGERED -> "Dead Man's Switch được kích hoạt do quá hạn check-in.";
            case DMS_TRIGGERED_NO_EXECUTOR -> "Dead Man's Switch được kích hoạt nhưng không có người thi hành khả dụng.";
            case EXECUTOR_ASSIGNED -> "Chỉ định người thi hành.";
            case EXECUTOR_REPLACED -> "Thay đổi người thi hành.";
            // Owner thu hồi luôn ghi actor = Owner; BR-03 (FR-07 tự thu hồi Primary) ghi actor = null
            case EXECUTOR_REVOKE -> log.getActor() == null
                    ? "Hệ thống tự động thu hồi người thi hành chính do không xác nhận đúng hạn."
                    : "Thu hồi người thi hành.";
            case EXECUTOR_ACTIVATED -> "Kích hoạt người thi hành sau khi Dead Man's Switch được kích hoạt.";
            case EXECUTOR_ACKNOWLEDGED -> "Người thi hành đã xác nhận nhận thông báo kích hoạt.";
            default -> "Thực hiện hành động: " + log.getAction();
        };
    }

    // AuditLogServiceImpl luôn ghi "result" (SUCCESS/FAILURE) vào metadata
    private boolean isFailure(AuditLog log) {
        return log.getMetadata() != null && "FAILURE".equals(log.getMetadata().get("result"));
    }
}
