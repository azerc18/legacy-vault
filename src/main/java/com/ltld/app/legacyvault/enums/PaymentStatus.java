package com.ltld.app.legacyvault.enums;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    /**
     * Người mua bỏ ngang: quá hạn mà cổng thanh toán xác nhận chưa trừ tiền (hoặc bị thay bằng đơn mới).
     * Khác FAILED ở chỗ IPN/return về muộn vẫn được xử lý như đơn PENDING, nên không bao giờ mất tiền của người mua.
     */
    EXPIRED
}

