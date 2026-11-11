package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.GrantVipRequest;
import com.ltld.app.legacyvault.dto.adminoverviewdto.RefundPaymentRequest;
import com.ltld.app.legacyvault.dto.adminoverviewdto.VipGrantResponse;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.PaymentProvider;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotifier;
import com.ltld.app.legacyvault.service.paymentservice.PaypalClient;
import com.ltld.app.legacyvault.service.paymentservice.VnpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Việc 3: Admin tặng VIP thủ công và hoàn tiền giao dịch.
 *
 * <p>Hoàn tiền chỉ trừ phần CHƯA DÙNG của kỳ mà giao dịch đó mua ({@code paidAt + số ngày gói - bây giờ}, không âm)
 * khỏi hạn VIP hiện tại. Giao dịch cũ đã dùng hết kỳ thì không trừ ngày nào, nên hoàn một giao dịch cũ không ăn vào
 * kỳ của giao dịch gia hạn mới hơn. Khi Owner đã gia hạn nối tiếp, kỳ thật của giao dịch sau bắt đầu muộn hơn
 * {@code paidAt}, nên cách tính này có thể trừ ít hơn thực tế: chấp nhận, vì lệch về phía có lợi cho Owner.
 * Nếu hạn mới còn dưới 1 phút thì gói chuyển EXPIRED ngay (Owner về FREE, dữ liệu giữ nguyên theo chính sách hết hạn).
 * Doanh thu chỉ tính SUCCESS nên giao dịch REFUNDED tự rời khỏi số liệu doanh thu.
 *
 * <p>Thông báo trong app chỉ gửi sau khi transaction commit, để không có thông báo "đã hoàn tiền" khi DB rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBillingService {

    static final int DEFAULT_DURATION_DAYS = 30;

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VnpayClient vnpayClient;
    private final PaypalClient paypalClient;
    private final AuditLogService auditLogService;
    private final InAppNotifier inAppNotifier;
    private final AdminPaymentService adminPaymentService;

    @Transactional
    public VipGrantResponse grantVip(UUID adminId, UUID userId, GrantVipRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found."));
        boolean owner = user.getRoles().stream().anyMatch(r -> "OWNER".equals(r.getCode()));
        if (!owner) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "VIP can only be granted to an Owner account.");
        }
        int days = request.days();
        LocalDateTime now = LocalDateTime.now();

        Optional<Subscription> activeOpt = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE);
        Subscription sub;
        if (activeOpt.isPresent()) {
            sub = activeOpt.get();
            LocalDateTime base = sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now) ? sub.getExpiresAt() : now;
            sub.setExpiresAt(base.plusDays(days));
            sub.setReminderSent(false);
            if (sub.getPlan() == null || !sub.getPlan().isVip()) {
                sub.setPlan(vipPlan());
            }
        } else {
            sub = Subscription.builder()
                    .user(user)
                    .plan(vipPlan())
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(now)
                    .expiresAt(now.plusDays(days))
                    .reminderSent(false)
                    .build();
        }
        subscriptionRepository.save(sub);

        auditLogService.log(AuditAction.ADMIN_VIP_GRANTED, AuditResult.SUCCESS, adminId, adminEmail(adminId),
                "User", userId.toString(), "days=" + days + " until=" + sub.getExpiresAt()
                        + " reason=" + request.reason().trim());
        LocalDateTime expiresAt = sub.getExpiresAt();
        afterCommit(() -> inAppNotifier.toUser(userId, InAppNotifier.draft(NotificationType.SUBSCRIPTION_GRANTED,
                null, null, "You received " + days + " VIP day(s).", "days", days, "expiresAt", expiresAt)));
        return new VipGrantResponse(sub.getPlan().getCode(), sub.getExpiresAt());
    }

    @Transactional
    public AdminPaymentResponse refund(UUID adminId, UUID transactionId, RefundPaymentRequest request) {
        String adminEmail = adminEmail(adminId);
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));
        if (tx.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only successful payments can be refunded.");
        }

        String refundRef = null;
        if (request.gateway()) {
            refundRef = refundAtGateway(tx, adminEmail);
            warnIfNotCommitted(tx, refundRef);
        }

        LocalDateTime now = LocalDateTime.now();
        tx.setStatus(PaymentStatus.REFUNDED);
        tx.setRefundedAt(now);
        tx.setRefundReason(request.reason().trim());
        tx.setRefundRef(refundRef);
        paymentTransactionRepository.save(tx);

        User user = tx.getUser();
        LocalDateTime newExpiry = revokeUnusedDays(user, tx, now);

        auditLogService.log(AuditAction.PAYMENT_REFUNDED, AuditResult.SUCCESS, adminId, adminEmail,
                "PaymentTransaction", tx.getId().toString(),
                (request.gateway() ? "gateway ref=" + refundRef : "manual") + " amount=" + tx.getAmountVnd()
                        + " reason=" + request.reason().trim());
        if (user != null) {
            UUID userId = user.getId();
            String amount = String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,d", tx.getAmountVnd());
            String plan = tx.getPlan() != null ? tx.getPlan().getName() : "VIP";
            afterCommit(() -> inAppNotifier.toUser(userId, InAppNotifier.draft(NotificationType.PAYMENT_REFUNDED,
                    null, null, "A payment was refunded.", "amount", amount, "plan", plan, "expiresAt", newExpiry)));
        }
        return adminPaymentService.describe(tx);
    }

    /** Gọi API hoàn tiền của cổng; cổng từ chối thì ném lỗi và không đổi gì trong DB. */
    private String refundAtGateway(PaymentTransaction tx, String adminEmail) {
        if (tx.getProvider() == PaymentProvider.PAYPAL) {
            String captureId = tx.getVnpTransactionNo();
            if (captureId == null || captureId.isBlank()) {
                throw new BusinessException(ErrorCode.CONFLICT, "This PayPal payment has no capture id to refund.");
            }
            PaypalClient.Refund refund;
            try {
                refund = paypalClient.refundCapture(captureId);
            } catch (RuntimeException ex) {
                log.warn("Refund: PayPal call for {} failed: {}", tx.getId(), ex.getMessage());
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Could not reach PayPal. Try again later.");
            }
            if (!refund.ok()) {
                throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                        "PayPal refused the refund (" + refund.status() + ").");
            }
            return refund.refundId();
        }
        LocalDateTime createdAt = tx.getCreatedAt() != null ? tx.getCreatedAt() : tx.getPaidAt();
        VnpayClient.RefundResult result = vnpayClient.refund(tx.getId(), tx.getAmountVnd(), tx.getVnpTransactionNo(),
                createdAt, adminEmail);
        if (!result.ok()) {
            if (result.responseCode() == null) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Could not reach VNPay. Try again later.");
            }
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                    "VNPay refused the refund (" + result.responseCode() + " " + result.message() + ").");
        }
        return result.transactionNo();
    }

    /**
     * Trừ phần chưa dùng của kỳ mà giao dịch đã mua khỏi hạn VIP đang chạy (xem javadoc lớp); trả hạn mới, null nếu
     * không còn gói nào chạy.
     */
    private LocalDateTime revokeUnusedDays(User user, PaymentTransaction tx, LocalDateTime now) {
        if (user == null) {
            return null;
        }
        userRepository.findByIdForUpdate(user.getId());
        Optional<Subscription> activeOpt = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(user.getId(), SubscriptionStatus.ACTIVE);
        if (activeOpt.isEmpty() || activeOpt.get().getExpiresAt() == null) {
            return null;
        }
        Subscription sub = activeOpt.get();
        Duration unused = unusedPart(tx, now);
        if (unused.isZero()) {
            return sub.getExpiresAt();
        }
        LocalDateTime newExpiry = sub.getExpiresAt().minus(unused);
        if (newExpiry.isBefore(now.plusMinutes(1))) {
            sub.setExpiresAt(now);
            sub.setStatus(SubscriptionStatus.EXPIRED);
        } else {
            sub.setExpiresAt(newExpiry);
        }
        subscriptionRepository.save(sub);
        return sub.getStatus() == SubscriptionStatus.ACTIVE ? sub.getExpiresAt() : null;
    }

    /** Phần chưa dùng của kỳ giao dịch đã mua: {@code paidAt + số ngày gói - now}, không âm, không quá số ngày gói. */
    static Duration unusedPart(PaymentTransaction tx, LocalDateTime now) {
        SubscriptionPlan plan = tx.getPlan();
        int days = plan != null && plan.getDurationDays() != null && plan.getDurationDays() > 0
                ? plan.getDurationDays() : DEFAULT_DURATION_DAYS;
        LocalDateTime start = tx.getPaidAt() != null ? tx.getPaidAt() : now;
        Duration left = Duration.between(now, start.plusDays(days));
        if (left.isNegative()) {
            return Duration.ZERO;
        }
        return left.compareTo(Duration.ofDays(days)) > 0 ? Duration.ofDays(days) : left;
    }

    private String adminEmail(UUID adminId) {
        return adminId == null ? null : userRepository.findById(adminId).map(User::getEmail).orElse(null);
    }

    /** Chạy sau khi commit; ngoài transaction (test đơn vị) thì chạy luôn. */
    private static void afterCommit(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }

    /**
     * Review #254 m1: cổng đã hoàn tiền nhưng bước ghi DB sau đó lỗi thì giao dịch vẫn SUCCESS. Log ERROR kèm mã hoàn
     * của cổng để đối chiếu rồi dùng "ghi nhận hoàn tay" (PayPal gọi lại tự khớp nhờ PayPal-Request-Id, VNPay thì không).
     */
    private static void warnIfNotCommitted(PaymentTransaction tx, String refundRef) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        UUID txId = tx.getId();
        PaymentProvider provider = tx.getProvider();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    log.error("Refund: {} refunded payment {} (refund ref {}) but the database update did not commit."
                            + " Record it with a manual refund after checking the gateway.", provider, txId, refundRef);
                }
            }
        });
    }

    private SubscriptionPlan vipPlan() {
        return subscriptionPlanRepository.findByCode(SubscriptionPlan.CODE_VIP_MONTH)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "VIP plan is not configured."));
    }
}
