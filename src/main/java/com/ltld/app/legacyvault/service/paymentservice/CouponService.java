package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.coupondto.AdminCouponResponse;
import com.ltld.app.legacyvault.dto.coupondto.CouponPreviewResponse;
import com.ltld.app.legacyvault.dto.coupondto.CouponQuote;
import com.ltld.app.legacyvault.dto.coupondto.CreateCouponRequest;
import com.ltld.app.legacyvault.entity.Coupon;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.CouponRepository;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.UUID;

/**
 * Việc 4: mã giảm giá theo %.
 *
 * <p>Giá sau giảm làm tròn tới 1.000 ₫ và không thấp hơn {@link #MIN_FINAL_VND} (cổng thanh toán có mức tối thiểu).
 * Mỗi tài khoản dùng một mã một lần. Lượt dùng chỉ tăng khi giao dịch thành công, nên đơn bỏ ngang không tốn lượt;
 * vì vậy hai người thanh toán cùng lúc lượt cuối có thể vượt {@code maxRedemptions} một chút (chấp nhận được, vì
 * người đã trả tiền luôn được nhận gói).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    static final int MAX_FAILED_GUESSES = 10;
    static final int GUESS_WINDOW_MINUTES = 10;
    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    static final long MIN_FINAL_VND = 10_000L;
    /** Giao dịch đã thu tiền: tính là đã dùng mã (kể cả đã hoàn, để không lách bằng cách xin hoàn rồi dùng lại). */
    private static final List<PaymentStatus> USED = List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);

    private final CouponRepository couponRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AuditLogService auditLogService;
    private final Map<UUID, Deque<Instant>> failedGuesses = new ConcurrentHashMap<>();

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** Kiểm tra mã cho một gói và trả giá mới; ném lỗi có mã riêng cho từng lý do để FE hiện đúng câu. */
    @Transactional(readOnly = true)
    public CouponQuote quote(UUID userId, String rawCode, SubscriptionPlan plan) {
        Coupon coupon = usableCoupon(userId, rawCode, LocalDateTime.now());
        if (!appliesTo(coupon, plan)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "This code does not apply to " + plan.getCode() + ".");
        }
        return price(coupon, plan);
    }

    /** Trang Bảng giá: giá mới của mọi gói VIP mà mã áp dụng được. */
    @Transactional(readOnly = true)
    public CouponPreviewResponse preview(UUID userId, String rawCode) {
        Coupon coupon = usableCoupon(userId, rawCode, LocalDateTime.now());
        List<CouponQuote> plans = subscriptionPlanRepository.findAllByOrderByPriceVndAsc().stream()
                .filter(p -> p.isVip() && p.getPriceVnd() > 0 && appliesTo(coupon, p))
                .map(p -> price(coupon, p))
                .toList();
        if (plans.isEmpty()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "This code does not apply to any plan.");
        }
        return new CouponPreviewResponse(coupon.getCode(), coupon.getPercentOff(), lastValidDay(coupon.getValidUntil()),
                plans);
    }

    /**
     * Gọi khi giao dịch có mã vừa được ghi SUCCESS (đã lưu). Tiền đã thu nên không bao giờ từ chối ở bước này.
     *
     * <p>Review #256 m1: người mua có thể mở nhiều đơn cùng mã (nhiều tab, bấm thanh toán lại; đơn cũ thành EXPIRED
     * nhưng vẫn trả được) rồi trả hết. Khi tài khoản đã có giao dịch khác cùng mã đã thu tiền thì không cộng lượt lần
     * nữa (không vượt maxRedemptions vì cùng một người) và ghi audit {@code COUPON_REUSED} để admin xem và hoàn tiền
     * nếu cần.
     */
    @Transactional
    public void redeem(PaymentTransaction tx) {
        String code = tx.getCouponCode();
        if (code == null || code.isBlank()) {
            return;
        }
        UUID userId = tx.getUser() != null ? tx.getUser().getId() : null;
        long used = userId == null ? 1
                : paymentTransactionRepository.countByUserIdAndCouponCodeIgnoreCaseAndStatusIn(userId, normalize(code), USED);
        if (used > 1) {
            log.warn("Coupon {} paid again by user {} on payment {}: money kept, redemption not counted twice",
                    normalize(code), userId, tx.getId());
            auditLogService.log(AuditAction.COUPON_REUSED, AuditResult.FAILURE, userId,
                    tx.getUser() != null ? tx.getUser().getEmail() : null, "PaymentTransaction", tx.getId().toString(),
                    normalize(code) + " used again by the same account; review and refund if needed");
            return;
        }
        couponRepository.incrementRedeemed(normalize(code));
    }

    static CouponQuote price(Coupon coupon, SubscriptionPlan plan) {
        long original = plan.getPriceVnd();
        long raw = original * (100 - coupon.getPercentOff()) / 100;
        long rounded = Math.round(raw / 1000.0) * 1000L;
        long finalVnd = Math.min(original, Math.max(MIN_FINAL_VND, rounded));
        return new CouponQuote(plan.getCode(), original, original - finalVnd, finalVnd);
    }

    private static boolean appliesTo(Coupon coupon, SubscriptionPlan plan) {
        return coupon.getPlanCode() == null || coupon.getPlanCode().equalsIgnoreCase(plan.getCode());
    }

    private Coupon usableCoupon(UUID userId, String rawCode, LocalDateTime now) {
        checkGuessRate(userId);
        String code = normalize(rawCode);
        Coupon coupon = code.isEmpty() ? null : couponRepository.findByCodeIgnoreCase(code).orElse(null);
        if (coupon == null || !coupon.isActive()
                || (coupon.getValidUntil() != null && !coupon.getValidUntil().isAfter(now))) {
            recordFailedGuess(userId);
            throw new BusinessException(ErrorCode.COUPON_INVALID, "This code is not valid or has expired.");
        }
        if (coupon.getMaxRedemptions() != null && coupon.getRedeemedCount() >= coupon.getMaxRedemptions()) {
            throw new BusinessException(ErrorCode.COUPON_USED_UP, "This code has been fully used.");
        }
        if (userId != null && paymentTransactionRepository
                .existsByUserIdAndCouponCodeIgnoreCaseAndStatusIn(userId, coupon.getCode(), USED)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED, "You have already used this code.");
        }
        return coupon;
    }

    /**
     * Review #256 n1: chặn đoán mã. Tối đa {@value #MAX_FAILED_GUESSES} lần nhập mã không tồn tại/hết hạn trong
     * {@value #GUESS_WINDOW_MINUTES} phút cho mỗi tài khoản. Lưu trong bộ nhớ (mất khi restart), giống giới hạn của
     * trợ lý Vaulty.
     */
    private void checkGuessRate(UUID userId) {
        Deque<Instant> q = userId == null ? null : failedGuesses.get(userId);
        if (q == null) {
            return;
        }
        synchronized (q) {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(GUESS_WINDOW_MINUTES));
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= MAX_FAILED_GUESSES) {
                throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS,
                        "Too many invalid codes. Please wait a few minutes.");
            }
        }
    }

    private void recordFailedGuess(UUID userId) {
        if (userId == null) {
            return;
        }
        Deque<Instant> q = failedGuesses.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(Instant.now());
        }
    }

    /** Ngày cuối cùng mã còn dùng được (giờ VN), tính từ mốc hết hạn đã lưu theo giờ server. */
    public static LocalDate lastValidDay(LocalDateTime validUntil) {
        return validUntil == null ? null : validUntil.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN_ZONE)
                .minusNanos(1).toLocalDate();
    }

    /** Hết ngày {@code day} theo giờ VN, đổi về giờ server để so với {@code LocalDateTime.now()}. */
    static LocalDateTime endOfVnDay(LocalDate day) {
        return day.plusDays(1).atStartOfDay(VN_ZONE).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    // ---- Admin ----

    @Transactional(readOnly = true)
    public List<AdminCouponResponse> list() {
        LocalDateTime now = LocalDateTime.now();
        return couponRepository.findAllByOrderByCreatedAtDesc().stream().map(c -> AdminCouponResponse.from(c, now)).toList();
    }

    @Transactional
    public AdminCouponResponse create(UUID adminId, CreateCouponRequest req) {
        String code = normalize(req.code());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Code " + code + " already exists.");
        }
        String planCode = req.planCode() == null || req.planCode().isBlank() ? null : normalize(req.planCode());
        if (planCode != null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findByCode(planCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Plan not found: " + planCode));
            if (!plan.isVip()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Codes only apply to VIP plans.");
            }
        }
        // Review #256 n2: "hết ngày 31/12" theo giờ VN, không theo giờ server (Azure chạy UTC)
        LocalDateTime validUntil = req.validUntil() == null ? null : endOfVnDay(req.validUntil());
        if (validUntil != null && !validUntil.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "The end date is already in the past.");
        }
        Coupon coupon = couponRepository.save(Coupon.builder()
                .code(code)
                .percentOff(req.percentOff())
                .planCode(planCode)
                .maxRedemptions(req.maxRedemptions())
                .validUntil(validUntil)
                .note(req.note() == null || req.note().isBlank() ? null : req.note().trim())
                .createdBy(adminId)
                .build());
        auditLogService.log(AuditAction.COUPON_CREATED, AuditResult.SUCCESS, adminId, null, "Coupon",
                coupon.getId().toString(), code + " -" + req.percentOff() + "%"
                        + (planCode != null ? " plan=" + planCode : "")
                        + (req.maxRedemptions() != null ? " max=" + req.maxRedemptions() : ""));
        return AdminCouponResponse.from(coupon, LocalDateTime.now());
    }

    @Transactional
    public AdminCouponResponse setActive(UUID adminId, UUID couponId, boolean active) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Code not found."));
        if (coupon.isActive() != active) {
            coupon.setActive(active);
            couponRepository.save(coupon);
            auditLogService.log(AuditAction.COUPON_UPDATED, AuditResult.SUCCESS, adminId, null, "Coupon",
                    coupon.getId().toString(), coupon.getCode() + (active ? " enabled" : " disabled"));
        }
        return AdminCouponResponse.from(coupon, LocalDateTime.now());
    }
}
