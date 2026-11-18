package com.ltld.app.legacyvault.enums;

/** V3: kết quả kiểm lại chữ ký xác nhận của Legal Verifier. */
public enum SignatureSealStatus {
    VALID,                 // chữ ký đúng khóa công khai, nội dung khớp hồ sơ, giấy chứng tử chưa bị thay
    INVALID,               // chữ ký sai, nội dung đã ký không khớp hồ sơ, hoặc giấy chứng tử đã bị thay
    DOCUMENT_UNAVAILABLE,  // chữ ký đúng nhưng tạm không đọc được file để so mã băm (storage lỗi)
    LEGACY                 // chữ ký tạo trước khi có tính năng này: chỉ có mã băm cũ, không kiểm lại được
}
