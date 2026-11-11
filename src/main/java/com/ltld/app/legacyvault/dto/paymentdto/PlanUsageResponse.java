package com.ltld.app.legacyvault.dto.paymentdto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Việc 2: mức đã dùng so với hạn mức gói hiện tại (GET /api/subscriptions/me/usage).
 *
 * <p>Chính sách khi VIP hết hạn: dữ liệu đã có giữ nguyên, vẫn xem / sửa / xoá được, lời nhắn và người xác nhận đã
 * khai vẫn hoạt động khi kích hoạt di sản. Chỉ chặn TẠO THÊM ở những mục đang vượt hạn mức FREE ({@code overLimit}).
 */
@Getter
@Builder
public class PlanUsageResponse {
    private String planCode;
    private boolean vip;
    /** Từng có VIP nhưng đã hết hạn (đang dùng FREE). */
    private boolean vipLapsed;
    /** Instant (có múi giờ) để FE hiện đúng ngày theo giờ người xem dù server chạy UTC. */
    private Instant vipExpiredAt;

    private long vaultsUsed;
    private int maxVaults;
    /** Số người thụ hưởng của vault đông nhất (hạn mức tính theo từng vault). */
    private long maxBeneficiariesInAVault;
    private int maxBeneficiariesPerVault;
    private long storageUsedBytes;
    private long maxStorageBytes;
    private long legacyMessages;
    private boolean legacyMessagesEnabled;

    /** Mục đang VƯỢT hạn mức (MULTIPLE_VAULTS, BENEFICIARY_LIMIT, STORAGE, LEGACY_MESSAGES) (thường do VIP hết hạn); dữ liệu giữ nguyên nhưng không thêm mới được. */
    private List<String> overLimit;
}
