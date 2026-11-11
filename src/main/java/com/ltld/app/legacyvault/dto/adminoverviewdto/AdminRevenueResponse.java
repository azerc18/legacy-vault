package com.ltld.app.legacyvault.dto.adminoverviewdto;

import java.time.LocalDate;
import java.util.List;

/**
 * Việc 5: báo cáo doanh thu cho Admin. Mọi số tiền là VND; ngày tính theo giờ Việt Nam.
 *
 * @param range       {@code 30d} (theo ngày) hoặc {@code 12m} (theo tháng)
 * @param from        ngày đầu kỳ (tính cả ngày đó)
 * @param to          ngày cuối kỳ (hôm nay)
 * @param buckets     đủ mọi ngày/tháng trong kỳ, kể cả kỳ không có giao dịch
 * @param current     tổng của kỳ đang xem
 * @param previous    tổng của kỳ liền trước, cùng độ dài, để so sánh
 * @param byPlan      đã thu theo gói, nhiều nhất trước
 * @param byProvider  đã thu theo cổng, nhiều nhất trước
 */
public record AdminRevenueResponse(
        String range,
        LocalDate from,
        LocalDate to,
        List<Bucket> buckets,
        Totals current,
        Totals previous,
        List<Slice> byPlan,
        List<Slice> byProvider) {

    /** Một cột: {@code start} là ngày (30d) hoặc ngày 1 của tháng (12m). */
    public record Bucket(LocalDate start, long collectedVnd, long paidCount, long refundedVnd) {}

    /**
     * @param collectedVnd      tiền đã thu theo ngày thanh toán (gồm cả giao dịch sau đó bị hoàn)
     * @param refundedVnd       tiền đã hoàn theo ngày hoàn
     * @param netVnd            {@code collectedVnd - refundedVnd}
     * @param checkoutCount     đơn tạo ra trong kỳ, trừ đơn bị thay khi người mua bấm thanh toán lại
     * @param conversionPercent đơn đã thanh toán / {@code checkoutCount}; null khi kỳ không có đơn nào
     */
    public record Totals(long collectedVnd, long refundedVnd, long netVnd, long paidCount, long refundCount,
                         long checkoutCount, Double conversionPercent, long averageOrderVnd, long discountVnd) {}

    /** {@code key} là mã gói hoặc tên cổng; {@code label} là tên gói (với cổng thì trùng key). */
    public record Slice(String key, String label, long collectedVnd, long paidCount) {}
}
