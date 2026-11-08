package com.ltld.app.legacyvault.dto.adminoverviewdto;

import lombok.Builder;
import lombok.Getter;

/** A2: tổng hợp theo đúng bộ lọc đang chọn. Doanh thu chỉ tính giao dịch SUCCESS. */
@Getter
@Builder
public class AdminPaymentSummaryResponse {
    private long totalRevenueVnd;
    private long successCount;
    private long pendingCount;
    private long failedCount;
}
