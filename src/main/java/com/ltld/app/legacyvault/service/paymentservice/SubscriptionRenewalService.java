package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Việc 6: hủy gia hạn cuối kỳ và bật lại.
 *
 * <p>Hệ thống không tự trừ tiền, nên "hủy" chỉ là ghi nhận ý định: gói vẫn ACTIVE và giữ mọi quyền VIP tới
 * {@code expiresAt}, không hoàn tiền; khi tới hạn job {@link SubscriptionExpiryScheduler} đưa về FREE như mọi gói
 * hết hạn. Mail nhắc trước 7 ngày vẫn gửi nhưng nói rõ gói sẽ kết thúc, kèm nút gia hạn phòng khi đổi ý. Owner đổi ý thì bật lại trước hạn; trả tiền gia hạn cũng tự bỏ cờ hủy.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionRenewalService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final PaymentService paymentService;

    @Transactional
    public SubscriptionMeResponse cancelAtPeriodEnd(UUID userId) {
        Subscription sub = runningVip(userId);
        if (!sub.isCancelAtPeriodEnd()) {
            sub.setCancelAtPeriodEnd(true);
            sub.setCancelledAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
            auditLogService.log(AuditAction.SUBSCRIPTION_CANCELLED, AuditResult.SUCCESS, userId, null,
                    "Subscription", sub.getId().toString(), "endsAt=" + sub.getExpiresAt());
        }
        return paymentService.getMySubscription(userId);
    }

    @Transactional
    public SubscriptionMeResponse resume(UUID userId) {
        Subscription sub = runningVip(userId);
        if (sub.isCancelAtPeriodEnd()) {
            sub.setCancelAtPeriodEnd(false);
            sub.setCancelledAt(null);
            subscriptionRepository.save(sub);
            auditLogService.log(AuditAction.SUBSCRIPTION_RESUMED, AuditResult.SUCCESS, userId, null,
                    "Subscription", sub.getId().toString(), "expiresAt=" + sub.getExpiresAt());
        }
        return paymentService.getMySubscription(userId);
    }

    /** Gói VIP còn hạn của Owner; khóa dòng users để không chạy song song với IPN gia hạn. */
    private Subscription runningVip(UUID userId) {
        userRepository.findByIdForUpdate(userId);
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE)
                .filter(s -> s.getPlan() != null && s.getPlan().isVip())
                .filter(s -> s.getExpiresAt() != null && s.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "You have no running VIP plan."));
    }
}
