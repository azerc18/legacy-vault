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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Việc 3: Admin tặng VIP thủ công và hoàn tiền giao dịch.
 *
 * <p>Hoàn tiền trừ lại đúng số ngày gói của giao dịch đó khỏi hạn VIP hiện tại; nếu hạn mới đã qua thì gói chuyển
 * EXPIRED ngay (Owner về FREE, dữ liệu giữ nguyên theo chính sách hết hạn). Doanh thu chỉ tính SUCCESS nên giao dịch
 * REFUNDED tự rời khỏi số liệu doanh thu.
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

        auditLogService.log(AuditAction.ADMIN_VIP_GRANTED, AuditResult.SUCCESS, adminId, null,
                "User", userId.toString(), "days=" + days + " until=" + sub.getExpiresAt()
                        + " reason=" + request.reason().trim());
        inAppNotifier.toUser(userId, InAppNotifier.draft(NotificationType.SUBSCRIPTION_GRANTED, null, null,
                "You received " + days + " VIP day(s).", "days", days, "expiresAt", sub.getExpiresAt()));
        return new VipGrantResponse(sub.getPlan().getCode(), sub.getExpiresAt());
    }

    @Transactional
    public AdminPaymentResponse refund(UUID adminId, UUID transactionId, RefundPaymentRequest request) {
        String adminEmail = adminId == null ? null
                : userRepository.findById(adminId).map(User::getEmail).orElse(null);
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));
        if (tx.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.CONFLICT, "Only successful payments can be refunded.");
        }

        String refundRef = null;
        if (request.gateway()) {
            refundRef = refundAtGateway(tx, adminEmail);
        }

        LocalDateTime now = LocalDateTime.now();
        tx.setStatus(PaymentStatus.REFUNDED);
        tx.setRefundedAt(now);
        tx.setRefundReason(request.reason().trim());
        tx.setRefundRef(refundRef);
        paymentTransactionRepository.save(tx);

        User user = tx.getUser();
        LocalDateTime newExpiry = revokeDays(user, tx.getPlan(), now);

        auditLogService.log(AuditAction.PAYMENT_REFUNDED, AuditResult.SUCCESS, adminId, adminEmail,
                "PaymentTransaction", tx.getId().toString(),
                (request.gateway() ? "gateway ref=" + refundRef : "manual") + " amount=" + tx.getAmountVnd()
                        + " reason=" + request.reason().trim());
        if (user != null) {
            inAppNotifier.toUser(user.getId(), InAppNotifier.draft(NotificationType.PAYMENT_REFUNDED,
                    null, null, "A payment was refunded.",
                    "amount", String.format(java.util.Locale.forLanguageTag("vi-VN"), "%,d", tx.getAmountVnd()),
                    "plan", tx.getPlan() != null ? tx.getPlan().getName() : "VIP",
                    "expiresAt", newExpiry));
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

    /** Trừ số ngày của gói đã hoàn khỏi hạn VIP đang chạy; trả hạn mới (null nếu không còn gói nào chạy). */
    private LocalDateTime revokeDays(User user, SubscriptionPlan plan, LocalDateTime now) {
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
        int days = plan != null && plan.getDurationDays() != null && plan.getDurationDays() > 0
                ? plan.getDurationDays() : DEFAULT_DURATION_DAYS;
        LocalDateTime newExpiry = sub.getExpiresAt().minusDays(days);
        if (!newExpiry.isAfter(now)) {
            sub.setExpiresAt(now);
            sub.setStatus(SubscriptionStatus.EXPIRED);
        } else {
            sub.setExpiresAt(newExpiry);
        }
        subscriptionRepository.save(sub);
        return sub.getStatus() == SubscriptionStatus.ACTIVE ? sub.getExpiresAt() : null;
    }

    private SubscriptionPlan vipPlan() {
        return subscriptionPlanRepository.findByCode(SubscriptionPlan.CODE_VIP_MONTH)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "VIP plan is not configured."));
    }
}
