package com.ltld.app.legacyvault.enums;

/** Kết quả đối chiếu số CCCD / CMND đọc từ giấy chứng tử với CCCD Owner đã xác minh. */
public enum OwnerIdMatch {
    /** Trùng số CCCD (12 số) hoặc CMND cũ (9 số) Owner đã khai. Bằng chứng mạnh. */
    MATCHED,
    /** Có số trên giấy nhưng khác số Owner đã khai. */
    MISMATCHED,
    /** Giấy chứng tử không ghi số giấy tờ (hoặc AI không đọc được). */
    NOT_ON_DOCUMENT,
    /** Có số trên giấy nhưng không đối chiếu được (Owner không khai CMND cũ, hoặc số không đúng định dạng). */
    UNVERIFIABLE,
    /** Owner chưa xác minh danh tính. */
    OWNER_NOT_VERIFIED
}
