package com.ltld.app.legacyvault.enums;

/** C.2: trạng thái xác thực của một tài sản. Không có dòng xác thực nào = SELF_DECLARED. */
public enum AssetVerificationStatus {
    SELF_DECLARED,      // Owner tự khai
    PENDING,            // đang chờ Owner hoàn tất bước xác thực
    VERIFIED,           // xác thực thật
    VERIFIED_SANDBOX,   // xác thực mô phỏng (hiện nhãn "Mô phỏng")
    FAILED,
    EXPIRED             // quá hạn, cần xác thực lại
}
