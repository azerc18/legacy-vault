package com.ltld.app.legacyvault.dto.integrationdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin chọn eKYC mô phỏng: {@code enabled} = true / false để ghi đè, null = quay về theo biến môi trường.
 * Lý do bắt buộc (ghi vào Nhật ký) vì bật mock nghĩa là chấp nhận mọi số CCCD đúng định dạng.
 */
public record UpdateKycMockRequest(Boolean enabled,
                                   @NotBlank(message = "Reason is required")
                                   @Size(min = 5, max = 300, message = "Reason must be 5–300 characters")
                                   String reason) {
}
