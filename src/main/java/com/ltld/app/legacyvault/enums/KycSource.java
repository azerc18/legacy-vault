package com.ltld.app.legacyvault.enums;

/** Nguồn dữ liệu CCCD khi Owner xác minh danh tính; Legal Verifier dùng để biết mức tin cậy. */
public enum KycSource {
    /** Quét mã QR trên CCCD gắn chip: tin cậy nhất. */
    QR,
    /** Đọc ảnh CCCD bằng AI rồi Owner xem lại. */
    OCR,
    /** Owner tự nhập. */
    MANUAL
}
