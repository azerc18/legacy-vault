package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultActivityLogServiceImpl implements VaultActivityLogService {

    private final AuditLogRepository auditLogRepository;
    private final VaultRepository vaultRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<VaultActivityLogResponse> getVaultActivityLogs(UUID vaultId, UUID requesterId, Instant startDate, Instant endDate, Pageable pageable) {
        // 1. Kiểm tra vault tồn tại
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        // 2. Phân quyền: Chỉ chủ vault mới xem được
        if (!vault.getOwner().getId().equals(requesterId)) {
            throw new VaultException("Unauthorized: You don't have permission to view logs for this vault", HttpStatus.FORBIDDEN);
        }

        // N6: Default dates and validation
        Instant to = endDate != null ? endDate : Instant.now();
        Instant from = startDate != null ? startDate : to.minus(30, java.time.temporal.ChronoUnit.DAYS);
        if (from.isAfter(to)) throw new VaultException("Ngày bắt đầu phải trước ngày kết thúc.", HttpStatus.BAD_REQUEST);
        if (java.time.Duration.between(from, to).toDays() > 365) throw new VaultException("Khoảng thời gian tối đa 1 năm.", HttpStatus.BAD_REQUEST);

        // 3. Truy vấn logs từ AuditLog (không cần bảng thứ hai)
        Page<AuditLog> logs = auditLogRepository.findVaultActivity(vaultId, from, to, pageable);

        // 4. Map sang Response & Masking Identity
        return logs.map(log -> mapToResponse(log, vault.getOwner().getId()));
    }

    private VaultActivityLogResponse mapToResponse(AuditLog log, UUID vaultOwnerId) {
        return VaultActivityLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .actorName(maskActorName(log.getActor(), vaultOwnerId))
                .description(buildDescription(log))
                .ipAddress(maskIpAddress(log.getActor(), vaultOwnerId, log.getIpAddress()))
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String maskActorName(User actor, UUID vaultOwnerId) {
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

    private String maskIpAddress(User actor, UUID vaultOwnerId, String ipAddress) {
        if (ipAddress == null) return null;
        if (actor != null && actor.getId().equals(vaultOwnerId)) {
            return ipAddress; // Chủ két thấy IP đầy đủ
        }
        return "***.***.***.***"; // Che giấu IP nếu không phải chủ két
    }

    private String buildDescription(AuditLog log) {
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
            default -> "Thực hiện hành động: " + log.getAction();
        };
    }

    // AuditLogServiceImpl luôn ghi "result" (SUCCESS/FAILURE) vào metadata
    private boolean isFailure(AuditLog log) {
        return log.getMetadata() != null && "FAILURE".equals(log.getMetadata().get("result"));
    }
}
