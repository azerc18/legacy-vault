package com.ltld.app.legacyvault.dto.notificationdto;

import com.ltld.app.legacyvault.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một thông báo trong app của người đang đăng nhập; FE dựa vào referenceType (không dựa vào type) để mở màn hình:
 * <ul>
 *   <li>{@code "Vault"}: referenceId = id Vault → trang Vault của Beneficiary (FR-16);</li>
 *   <li>{@code "LegalVerificationRequest"}: referenceId = id hồ sơ xác minh → trang hồ sơ của Executor
 *       (FR-11 / FR-12 / FR-14). Trang Executor điều hướng theo assignmentId, nên FE tra hồ sơ trong
 *       danh sách hồ sơ của từng assignment.</li>
 * </ul>
 * Cùng type VAULT_UNLOCKED có thể gửi cho cả hai vai trò, phân biệt bằng referenceType.
 */
@Getter
@Builder
public class NotificationResponse {
    private final UUID id;
    private final NotificationType type;
    private final String content;
    private final String referenceType;
    private final UUID referenceId;
    private final boolean read;
    private final LocalDateTime createdAt;
}
