package com.ltld.app.legacyvault.dto.systempolicydto;

/**
 * Xem trước khi lưu: số DMS đang chạy (ACTIVE / PENDING_CONFIRMATION) sẽ nằm ngoài khoảng mới.
 * Chỉ đọc, không ghi DB, không audit.
 */
public record DmsPolicyImpactResponse(long configsOutsideLimits) {
}
