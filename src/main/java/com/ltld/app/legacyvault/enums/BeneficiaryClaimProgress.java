package com.ltld.app.legacyvault.enums;

// N2-exec: trạng thái nhận tài sản của một người thụ hưởng, Executor xem để biết ai cần nhắc
public enum BeneficiaryClaimProgress {
    PENDING,
    ACCEPTED,
    DECLINED,
    // Đã từ chối và ít nhất một phần tài sản đã chuyển sang người nhận dự phòng
    FALLBACK_APPLIED
}
