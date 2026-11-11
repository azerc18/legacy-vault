package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentFilter;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentSummaryResponse;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A2: Admin xem giao dịch thanh toán. CHỈ ĐỌC: không gọi VNPay/PayPal, không đổi trạng thái giao dịch hay gói.
 */
@Service
public class AdminPaymentService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPORT_ROWS = 10_000;
    static final String PROVIDER_VNPAY = "VNPAY";

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> search(AdminPaymentFilter filter, int page, int size) {
        validate(filter);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Long total = bind(em.createQuery("SELECT COUNT(p) FROM PaymentTransaction p" + where(filter), Long.class), filter)
                .getSingleResult();
        List<PaymentTransaction> rows = fetch(filter, p * s, s);
        return new PageImpl<>(toResponses(rows), PageRequest.of(p, s), total);
    }

    @Transactional(readOnly = true)
    public AdminPaymentSummaryResponse summary(AdminPaymentFilter filter) {
        validate(filter);
        long revenue = 0;
        Map<PaymentStatus, Long> counts = new HashMap<>();
        List<Object[]> rows = bind(em.createQuery(
                "SELECT p.status, COUNT(p), COALESCE(SUM(p.amountVnd), 0) FROM PaymentTransaction p"
                        + where(filter) + " GROUP BY p.status", Object[].class), filter).getResultList();
        for (Object[] r : rows) {
            PaymentStatus st = (PaymentStatus) r[0];
            counts.put(st, ((Number) r[1]).longValue());
            if (st == PaymentStatus.SUCCESS) revenue = ((Number) r[2]).longValue();
        }
        return AdminPaymentSummaryResponse.builder()
                .totalRevenueVnd(revenue)
                .successCount(counts.getOrDefault(PaymentStatus.SUCCESS, 0L))
                .pendingCount(counts.getOrDefault(PaymentStatus.PENDING, 0L))
                .failedCount(counts.getOrDefault(PaymentStatus.FAILED, 0L))
                .expiredCount(counts.getOrDefault(PaymentStatus.EXPIRED, 0L))
                .build();
    }

    /** B4: toàn bộ giao dịch khớp bộ lọc (tối đa MAX_EXPORT_ROWS) để xuất CSV. */
    @Transactional(readOnly = true)
    public List<AdminPaymentResponse> export(AdminPaymentFilter filter) {
        validate(filter);
        return toResponses(fetch(filter, 0, MAX_EXPORT_ROWS));
    }

    private List<PaymentTransaction> fetch(AdminPaymentFilter filter, int offset, int limit) {
        return bind(em.createQuery("SELECT p FROM PaymentTransaction p JOIN FETCH p.user u JOIN FETCH p.plan"
                        + where(filter).replace("p.user.", "u.") + " ORDER BY p.createdAt DESC, p.id DESC",
                PaymentTransaction.class), filter)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    private static void validate(AdminPaymentFilter f) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "'from' must be before or equal to 'to'.");
        }
    }

    private static String where(AdminPaymentFilter f) {
        StringBuilder sb = new StringBuilder(" WHERE 1 = 1");
        if (f.status() != null) sb.append(" AND p.status = :status");
        if (hasEmail(f)) sb.append(" AND LOWER(p.user.email) LIKE :email");
        if (f.from() != null) sb.append(" AND p.createdAt >= :from");
        if (f.to() != null) sb.append(" AND p.createdAt < :toExclusive");
        return sb.toString();
    }

    private static <T> TypedQuery<T> bind(TypedQuery<T> q, AdminPaymentFilter f) {
        if (f.status() != null) q.setParameter("status", f.status());
        if (hasEmail(f)) q.setParameter("email", "%" + escapeLike(f.email().trim().toLowerCase(Locale.ROOT)) + "%");
        if (f.from() != null) q.setParameter("from", f.from().atStartOfDay());
        if (f.to() != null) q.setParameter("toExclusive", f.to().plusDays(1).atStartOfDay());
        return q;
    }

    private static boolean hasEmail(AdminPaymentFilter f) {
        return f.email() != null && !f.email().isBlank();
    }

    // Không cho người dùng chèn ký tự đại diện của LIKE
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<AdminPaymentResponse> toResponses(List<PaymentTransaction> rows) {
        if (rows.isEmpty()) return List.of();
        List<UUID> userIds = rows.stream().map(p -> p.getUser().getId()).distinct().toList();
        Map<UUID, String> currentPlan = currentPlanCodes(userIds);
        return rows.stream().map(p -> {
            SubscriptionPlan plan = p.getPlan();
            return AdminPaymentResponse.builder()
                    .id(p.getId())
                    .userId(p.getUser().getId())
                    .userEmail(p.getUser().getEmail())
                    .userFullName(p.getUser().getFullName())
                    .planCode(plan != null ? plan.getCode() : null)
                    .planName(plan != null ? plan.getName() : null)
                    .amountVnd(p.getAmountVnd())
                    .status(p.getStatus())
                    .provider(PROVIDER_VNPAY)
                    .providerTransactionNo(p.getVnpTransactionNo())
                    .bankCode(p.getVnpBankCode())
                    .responseCode(p.getVnpResponseCode())
                    .createdAt(p.getCreatedAt())
                    .paidAt(p.getPaidAt())
                    .currentPlanCode(currentPlan.getOrDefault(p.getUser().getId(), SubscriptionPlan.CODE_FREE))
                    .build();
        }).toList();
    }

    /** Gói trả phí còn hạn của từng user (nếu nhiều gói ACTIVE thì lấy gói hết hạn muộn nhất). */
    Map<UUID, String> currentPlanCodes(List<UUID> userIds) {
        List<Subscription> subs = em.createQuery("""
                        SELECT s FROM Subscription s JOIN FETCH s.plan
                        WHERE s.user.id IN :ids AND s.status = :status
                          AND (s.expiresAt IS NULL OR s.expiresAt > :now)
                        """, Subscription.class)
                .setParameter("ids", userIds)
                .setParameter("status", SubscriptionStatus.ACTIVE)
                .setParameter("now", LocalDateTime.now())
                .getResultList();
        Map<UUID, Subscription> best = new HashMap<>();
        for (Subscription s : subs) {
            if (!s.getPlan().isVip()) continue;
            best.merge(s.getUser().getId(), s, (a, b) -> later(a, b) ? a : b);
        }
        Map<UUID, String> out = new HashMap<>();
        best.forEach((id, s) -> out.put(id, s.getPlan().getCode()));
        return out;
    }

    private static boolean later(Subscription a, Subscription b) {
        if (a.getExpiresAt() == null) return true;
        if (b.getExpiresAt() == null) return false;
        return !a.getExpiresAt().isBefore(b.getExpiresAt());
    }
}
