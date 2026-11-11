package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse;
import com.ltld.app.legacyvault.enums.PaymentProvider;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminRevenueService.Range;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminRevenueService.Row;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminRevenueServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private static LocalDateTime at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 10, 0);
    }

    private static Row paid(LocalDateTime when, long amount, String plan, PaymentProvider provider) {
        return new Row(when, when, null, PaymentStatus.SUCCESS, provider, plan, plan + " name", amount, null);
    }

    @Test
    void range_parse_acceptsKnownCodesOnly() {
        assertThat(Range.parse("30d")).isEqualTo(Range.DAYS_30);
        assertThat(Range.parse("12M")).isEqualTo(Range.MONTHS_12);
        assertThatThrownBy(() -> Range.parse("7d")).isInstanceOf(BusinessException.class);
    }

    @Test
    void days30_fillsEveryDayAndSumsByPaidDay() {
        List<Row> rows = List.of(
                paid(at(11, 11), 199_000, "VIP_MONTH", PaymentProvider.VNPAY),
                paid(at(11, 11), 99_000, "VIP_MONTH", PaymentProvider.PAYPAL),
                paid(at(10, 13), 1_990_000, "VIP_YEAR", PaymentProvider.VNPAY),
                paid(at(10, 12), 500_000, "VIP_YEAR", PaymentProvider.VNPAY));   // ngày 31 về trước: kỳ trước

        AdminRevenueResponse r = AdminRevenueService.aggregate(Range.DAYS_30, TODAY, rows);

        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(r.buckets()).hasSize(30);
        assertThat(r.buckets().getFirst().collectedVnd()).isEqualTo(1_990_000);
        assertThat(r.buckets().getLast().collectedVnd()).isEqualTo(298_000);
        assertThat(r.buckets().getLast().paidCount()).isEqualTo(2);
        assertThat(r.current().collectedVnd()).isEqualTo(2_288_000);
        assertThat(r.current().averageOrderVnd()).isEqualTo(762_667);
        assertThat(r.previous().collectedVnd()).isEqualTo(500_000);
    }

    @Test
    void months12_bucketsByMonth() {
        List<Row> rows = List.of(
                paid(LocalDateTime.of(2025, 12, 1, 0, 0), 100_000, "A", PaymentProvider.VNPAY),
                paid(LocalDateTime.of(2025, 11, 30, 23, 59), 70_000, "A", PaymentProvider.VNPAY),
                paid(at(11, 2), 200_000, "A", PaymentProvider.VNPAY));

        AdminRevenueResponse r = AdminRevenueService.aggregate(Range.MONTHS_12, TODAY, rows);

        assertThat(r.buckets()).hasSize(12);
        assertThat(r.buckets().getFirst().start()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(r.buckets().getFirst().collectedVnd()).isEqualTo(100_000);
        assertThat(r.buckets().getLast().start()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.buckets().getLast().collectedVnd()).isEqualTo(200_000);
        assertThat(r.previous().collectedVnd()).isEqualTo(70_000);
    }

    @Test
    void refund_countsCollectedOnPaidDayAndRefundOnRefundDay() {
        Row refunded = new Row(at(10, 20), at(10, 20), at(11, 5), PaymentStatus.REFUNDED, PaymentProvider.VNPAY,
                "VIP_MONTH", "VIP", 199_000, null);

        AdminRevenueResponse r = AdminRevenueService.aggregate(Range.DAYS_30, TODAY, List.of(refunded));

        assertThat(r.current().collectedVnd()).isEqualTo(199_000);
        assertThat(r.current().refundedVnd()).isEqualTo(199_000);
        assertThat(r.current().netVnd()).isZero();
        assertThat(r.current().refundCount()).isEqualTo(1);
        assertThat(r.buckets().stream().filter(b -> b.start().equals(LocalDate.of(2026, 11, 5))).findFirst()
                .orElseThrow().refundedVnd()).isEqualTo(199_000);
    }

    @Test
    void conversion_isPaidOverCreatedInPeriod_andNullWithoutOrders() {
        List<Row> rows = List.of(
                paid(at(11, 1), 100_000, "A", PaymentProvider.VNPAY),
                new Row(at(11, 2), null, null, PaymentStatus.EXPIRED, PaymentProvider.VNPAY, "A", "A", 100_000, null),
                new Row(at(11, 3), null, null, PaymentStatus.FAILED, PaymentProvider.PAYPAL, "A", "A", 100_000, null));

        AdminRevenueResponse r = AdminRevenueService.aggregate(Range.DAYS_30, TODAY, rows);

        assertThat(r.current().checkoutCount()).isEqualTo(3);
        assertThat(r.current().conversionPercent()).isEqualTo(33.3);
        assertThat(r.previous().conversionPercent()).isNull();
        assertThat(r.previous().averageOrderVnd()).isZero();
    }

    @Test
    void slices_rankedByCollectedThenKey_andDiscountSummed() {
        List<Row> rows = List.of(
                paid(at(11, 1), 100_000, "B", PaymentProvider.PAYPAL),
                paid(at(11, 2), 300_000, "A", PaymentProvider.VNPAY),
                new Row(at(11, 3), at(11, 3), null, PaymentStatus.SUCCESS, PaymentProvider.VNPAY, "B", "B name",
                        90_000, 10_000L),
                new Row(at(11, 4), null, null, PaymentStatus.PENDING, PaymentProvider.VNPAY, "C", "C", 1, null));

        AdminRevenueResponse r = AdminRevenueService.aggregate(Range.DAYS_30, TODAY, rows);

        assertThat(r.byPlan()).extracting(AdminRevenueResponse.Slice::key).containsExactly("A", "B");
        assertThat(r.byPlan().get(1).collectedVnd()).isEqualTo(190_000);
        assertThat(r.byPlan().get(1).paidCount()).isEqualTo(2);
        assertThat(r.byProvider()).extracting(AdminRevenueResponse.Slice::key).containsExactly("VNPAY", "PAYPAL");
        assertThat(r.current().discountVnd()).isEqualTo(10_000);
    }
}
