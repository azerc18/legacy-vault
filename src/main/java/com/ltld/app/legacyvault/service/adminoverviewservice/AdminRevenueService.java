package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse.Bucket;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse.Slice;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse.Totals;
import com.ltld.app.legacyvault.enums.PaymentProvider;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Việc 5: biểu đồ doanh thu cho Admin. Chỉ đọc. Lấy các giao dịch chạm vào kỳ đang xem hoặc kỳ liền trước (tối đa
 * 24 tháng) rồi gom trong Java: lượng giao dịch hiện tại nhỏ, và một chỗ gom duy nhất dễ kiểm hơn nhiều câu GROUP BY.
 * Ngày tính theo giờ server như {@code paidAt}, giống biểu đồ ở trang Tổng quan.
 */
@Service
public class AdminRevenueService {

    /** Giao dịch đã thu tiền: kể cả giao dịch sau đó bị hoàn (tiền hoàn tính riêng theo ngày hoàn). */
    static final Set<PaymentStatus> COLLECTED = Set.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    public enum Range {
        DAYS_30("30d"), MONTHS_12("12m");

        final String code;

        Range(String code) {
            this.code = code;
        }

        public static Range parse(String value) {
            for (Range r : values()) {
                if (r.code.equalsIgnoreCase(value)) return r;
            }
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "range must be 30d or 12m.");
        }

        LocalDate start(LocalDate today) {
            return this == DAYS_30 ? today.minusDays(29) : today.withDayOfMonth(1).minusMonths(11);
        }

        LocalDate previousStart(LocalDate start) {
            return this == DAYS_30 ? start.minusDays(30) : start.minusMonths(12);
        }

        LocalDate bucketOf(LocalDate day) {
            return this == DAYS_30 ? day : day.withDayOfMonth(1);
        }

        LocalDate next(LocalDate bucket) {
            return this == DAYS_30 ? bucket.plusDays(1) : bucket.plusMonths(1);
        }
    }

    /** Những cột cần cho báo cáo, đọc một lần. */
    record Row(LocalDateTime createdAt, LocalDateTime paidAt, LocalDateTime refundedAt, PaymentStatus status,
               PaymentProvider provider, String planCode, String planName, long amountVnd, Long discountVnd) {}

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public AdminRevenueResponse report(String rangeCode) {
        Range range = Range.parse(rangeCode);
        LocalDate today = LocalDate.now();
        LocalDateTime since = range.previousStart(range.start(today)).atStartOfDay();
        List<Row> rows = em.createQuery("""
                        SELECT p.createdAt, p.paidAt, p.refundedAt, p.status, p.provider, pl.code, pl.name,
                               p.amountVnd, p.discountVnd
                        FROM PaymentTransaction p JOIN p.plan pl
                        WHERE p.createdAt >= :since OR p.paidAt >= :since OR p.refundedAt >= :since""", Object[].class)
                .setParameter("since", since)
                .getResultList().stream()
                .map(r -> new Row((LocalDateTime) r[0], (LocalDateTime) r[1], (LocalDateTime) r[2],
                        (PaymentStatus) r[3], (PaymentProvider) r[4], (String) r[5], (String) r[6],
                        ((Number) r[7]).longValue(), r[8] == null ? null : ((Number) r[8]).longValue()))
                .toList();
        return aggregate(range, today, rows);
    }

    /** Phần tính toán thuần, tách riêng để kiểm không cần cơ sở dữ liệu. */
    static AdminRevenueResponse aggregate(Range range, LocalDate today, List<Row> rows) {
        LocalDate from = range.start(today);
        LocalDate prevFrom = range.previousStart(from);

        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();   // [collected, paidCount, refunded]
        for (LocalDate b = from; !b.isAfter(today); b = range.next(b)) buckets.put(b, new long[3]);
        Map<String, Slice> byPlan = new LinkedHashMap<>();
        Map<String, Slice> byProvider = new LinkedHashMap<>();

        for (Row r : rows) {
            if (COLLECTED.contains(r.status()) && r.paidAt() != null && in(r.paidAt(), from, today)) {
                long[] b = buckets.get(range.bucketOf(r.paidAt().toLocalDate()));
                b[0] += r.amountVnd();
                b[1]++;
                byPlan.merge(r.planCode(), new Slice(r.planCode(), r.planName(), r.amountVnd(), 1), AdminRevenueService::add);
                String provider = r.provider().name();
                byProvider.merge(provider, new Slice(provider, provider, r.amountVnd(), 1), AdminRevenueService::add);
            }
            if (r.status() == PaymentStatus.REFUNDED && r.refundedAt() != null && in(r.refundedAt(), from, today)) {
                buckets.get(range.bucketOf(r.refundedAt().toLocalDate()))[2] += r.amountVnd();
            }
        }

        List<Bucket> points = new ArrayList<>(buckets.size());
        buckets.forEach((start, v) -> points.add(new Bucket(start, v[0], v[1], v[2])));
        return new AdminRevenueResponse(range.code, from, today, points,
                totals(rows, from, today), totals(rows, prevFrom, from.minusDays(1)),
                ranked(byPlan), ranked(byProvider));
    }

    static Totals totals(List<Row> rows, LocalDate from, LocalDate to) {
        long collected = 0, refunded = 0, paid = 0, refunds = 0, discount = 0, created = 0, createdAndPaid = 0;
        for (Row r : rows) {
            boolean wasCollected = COLLECTED.contains(r.status()) && r.paidAt() != null;
            if (wasCollected && in(r.paidAt(), from, to)) {
                collected += r.amountVnd();
                paid++;
                discount += r.discountVnd() == null ? 0 : r.discountVnd();
            }
            if (r.status() == PaymentStatus.REFUNDED && r.refundedAt() != null && in(r.refundedAt(), from, to)) {
                refunded += r.amountVnd();
                refunds++;
            }
            if (r.createdAt() != null && in(r.createdAt(), from, to)) {
                created++;
                if (wasCollected) createdAndPaid++;
            }
        }
        Double conversion = created == 0 ? null : Math.round(createdAndPaid * 1000.0 / created) / 10.0;
        long average = paid == 0 ? 0 : Math.round((double) collected / paid);
        return new Totals(collected, refunded, collected - refunded, paid, refunds, created, conversion, average, discount);
    }

    private static boolean in(LocalDateTime at, LocalDate from, LocalDate to) {
        LocalDate day = at.toLocalDate();
        return !day.isBefore(from) && !day.isAfter(to);
    }

    private static Slice add(Slice a, Slice b) {
        return new Slice(a.key(), a.label(), a.collectedVnd() + b.collectedVnd(), a.paidCount() + b.paidCount());
    }

    private static List<Slice> ranked(Map<String, Slice> m) {
        return m.values().stream()
                .sorted(Comparator.comparingLong(Slice::collectedVnd).reversed().thenComparing(Slice::key))
                .toList();
    }
}
