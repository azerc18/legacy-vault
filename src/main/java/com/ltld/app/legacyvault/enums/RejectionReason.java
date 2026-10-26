package com.ltld.app.legacyvault.enums;

/** T-4: lý do từ chối hồ sơ xác minh (Verifier chọn trong dropdown). */
public enum RejectionReason {
    DOCUMENT_UNREADABLE("Tài liệu mờ, không đọc được"),
    DOCUMENT_INVALID("Tài liệu không phải giấy chứng tử hợp lệ"),
    INFO_MISMATCH("Thông tin không khớp với chủ tài khoản"),
    SUSPECTED_FORGERY("Nghi ngờ tài liệu bị giả mạo"),
    DOCUMENT_INCOMPLETE("Tài liệu thiếu trang / thiếu dấu xác nhận"),
    OTHER("Lý do khác");

    private final String label;

    RejectionReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
