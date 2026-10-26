package com.ltld.app.legacyvault.dto.legalverificationdto;

/** T-4: một lựa chọn trong dropdown lý do từ chối (FE hiển thị label, gửi lại code). */
public record RejectionReasonDto(String code, String label) {
}
