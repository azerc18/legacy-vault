package com.ltld.app.legacyvault.dto.auditdto;

import jakarta.validation.constraints.Size;

/** B3: ghi chú tuỳ chọn khi Admin xác nhận đã xử lý cảnh báo. */
public record AcknowledgeAlertRequest(
        @Size(max = 500, message = "The note must be at most 500 characters") String note) {
}
