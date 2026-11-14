package com.ltld.app.legacyvault.enums;

/** Các chức năng gọi AI (Gemini) mà Admin bật / tắt và chọn model được ở trang Cấu hình AI. */
public enum AiFeature {
    /** Trợ lý Vaulty (chat). */
    ASSISTANT,
    /** Đọc giấy chứng tử cho Executor / Legal Verifier. */
    DEATH_CERTIFICATE_OCR,
    /** Đọc ảnh CCCD khi Owner xác minh danh tính (eKYC). */
    CITIZEN_ID_OCR
}
