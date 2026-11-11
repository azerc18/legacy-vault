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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Việc 5: biểu đồ doanh thu cho Admin. Chỉ đọc. Lấy các giao dịch chạm vào kỳ đang xem hoặc kỳ liền trước (tối đa
 * 24 tháng) rồi gom trong Java: lượng giao dịch hiện tại nhỏ, và một chỗ gom duy nhất dễ kiểm hơn nhiều câu GROUP BY.
 * Câu query lọc bằng OR trên 3 cột ngày nên quét cả bảng {@code payment_transactions}; ổn với lượng giao dịch hiện
 * tại, khi bảng lớn thì tách thành 3 query theo từng cột (mỗi cột một index) rồi gộp.
 *
 * <p>Ngày tính theo giờ Việt Nam: {@code paidAt}/{@code refundedAt}/{@code createdAt} lưu theo giờ server (Azure chạy
 * UTC) nên được đổi sang giờ VN trước khi gom, và "hôm nay" cũng lấy theo giờ VN.
 */
@Service
public class AdminRevenueService {

    /** Giao dịch đã thu tiền: kể cả giao dịch sau đó bị hoàn (tiền hoàn tính riêng theo ngày hoàn). */
    static final Set<PaymentStatus> COLLECTED = Set.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Lý do đóng đơn khi người mua bấm thanh toán lại (#251): không tính vào mẫu số tỉ lệ thanh toán. */
    static final String SUPERSEDED = "SUPERSEDED";

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
               PaymentProvider provider, String planCode, String planName, long amountVnd, Long discountVnd,
               String reason) {

        boolean superseded() {
            return status == PaymentStatus.EXPIRED && SUPERSEDED.equals(reason);
        }
    }

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public AdminRevenueResponse report(String rangeCode) {
        Range range = Range.parse(rangeCode);
        ZoneId server = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(VN_ZONE);
        LocalDateTime since = range.previousStart(range.start(today)).atStartOfDay(VN_ZONE)
                .withZoneSameInstant(server).toLocalDateTime();
        List<Row> rows = em.createQuery("""
                        SELECT p.createdAt, p.paidAt, p.refundedAt, p.status, p.provider, pl.code, pl.name,
                               p.amountVnd, p.discountVnd, p.vnpResponseCode
                        FROM PaymentTransaction p JOIN p.plan pl
                        WHERE p.createdAt >= :since OR p.paidAt >= :since OR p.refundedAt >= :since""", Object[].class)
                .setParameter("since", since)
                .getResultList().stream()
                .map(r -> new Row(toVn((LocalDateTime) r[0], server), toVn((LocalDateTime) r[1], server),
                        toVn((LocalDateTime) r[2], server), (PaymentStatus) r[3], (PaymentProvider) r[4],
                        (String) r[5], (String) r[6], ((Number) r[7]).longValue(),
                        r[8] == null ? null : ((Number) r[8]).longValue(), (String) r[9]))
                .toList();
        return aggregate(range, today, rows);
    }

    /** Giờ server (cách các cột LocalDateTime được ghi) sang giờ Việt Nam. */
    static LocalDateTime toVn(LocalDateTime serverTime, ZoneId server) {
        return serverTime == null ? null
                : serverTime.atZone(server).withZoneSameInstant(VN_ZONE).toLocalDateTime();
    }

    /** Phần tính toán thuần, tách riêng để kiểm không cần cơ sở dữ liệu. Mọi mốc thời gian đã là giờ VN. */
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
            // Đơn bị thay bằng đơn mới khi bấm thanh toán lại không phải một lần mua riêng (review #257 m2)
            if (r.createdAt() != null && !r.superseded() && in(r.createdAt(), from, to)) {
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
