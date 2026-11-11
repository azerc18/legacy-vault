package com.ltld.app.legacyvault.dto.adminoverviewdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Việc 3: Admin hoàn tiền một giao dịch SUCCESS.
 *
 * @param viaGateway true (mặc định): gọi API hoàn tiền của VNPay / PayPal. false: chỉ ghi nhận đã hoàn tay
 *                   (đã chuyển khoản trả lại ngoài hệ thống), không gọi cổng.
 */
public record RefundPaymentRequest(
        @NotBlank(message = "Reason is required")
        @Size(max = 255, message = "Reason must be at most 255 characters") String reason,
        Boolean viaGateway) {

    public boolean gateway() {
        return viaGateway == null || viaGateway;
    }
}
