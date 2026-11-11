package com.ltld.app.legacyvault.service.paymentservice;

/** Kết quả đối soát một đơn PENDING quá hạn. */
public enum ReconcileOutcome {
    /** Cổng xác nhận đã trừ tiền (IPN/return bị lỡ): đơn SUCCESS, gói đã kích hoạt. */
    ACTIVATED,
    /** Người mua chưa trả tiền: đơn EXPIRED. */
    EXPIRED,
    /** Cổng báo trả tiền nhưng sai số tiền, hoặc capture bị từ chối: đơn FAILED. */
    FAILED,
    /** Chưa hỏi được cổng: để PENDING tới lần chạy sau. */
    STILL_PENDING,
    /** Đơn không còn PENDING (IPN vừa về) hoặc không tồn tại. */
    SKIPPED
}
