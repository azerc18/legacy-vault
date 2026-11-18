package com.ltld.app.legacyvault.dto.systempolicydto;

import java.time.LocalDateTime;

/**
 * Trang Chính sách hệ thống, phần DMS: chính sách hiện tại, giới hạn cứng để form chặn sớm,
 * ai sửa lần cuối, và số DMS đang chạy có cấu hình nằm ngoài giới hạn hiện tại
 * (vẫn chạy bình thường, chỉ khi Owner lưu lại mới phải chỉnh cho vừa).
 * {@code hardLimits.backupActivationWaitDays} là số ngày TỐI ĐA (tối thiểu luôn là 1).
 */
public record AdminDmsPolicyResponse(DmsPolicy policy,
                                     DmsPolicy hardLimits,
                                     long configsOutsideLimits,
                                     LocalDateTime updatedAt,
                                     String updatedByEmail) {
}
