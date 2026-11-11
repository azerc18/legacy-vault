package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Việc 4: dùng thử VIP miễn phí, mỗi tài khoản Owner một lần (chưa từng có gói nào và chưa từng trả tiền).
 *
 * <p>Bản dùng thử là gói VIP_MONTH bình thường có {@code trial=true}, hạn {@code app.subscription.trial-days} ngày.
 * Không gửi mail "sắp hết hạn" (cả kỳ dùng thử đã ngắn hơn 7 ngày nhắc); hết hạn thì job hết hạn đưa về FREE và gửi
 * mail có nút "Gia hạn 1 chạm" như mọi gói. Trả tiền trong lúc dùng thử thì thời gian mới cộng nối tiếp.
 */
@Service
public class TrialService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AuditLogService auditLogService;
    private final PaymentService paymentService;
    private final int trialDays;

    public TrialService(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                        SubscriptionPlanRepository subscriptionPlanRepository,
                        PaymentTransactionRepository paymentTransactionRepository, AuditLogService auditLogService,
                        PaymentService paymentService, @Value("${app.subscription.trial-days:7}") int trialDays) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.auditLogService = auditLogService;
        this.paymentService = paymentService;
        this.trialDays = trialDays;
    }

    @Transactional
    public SubscriptionMeResponse start(UUID userId) {
        // Khóa dòng users để hai lần bấm cùng lúc không tạo hai bản dùng thử
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found."));
        if (subscriptionRepository.existsByUserId(userId)
                || paymentTransactionRepository.existsByUserIdAndStatus(userId, PaymentStatus.SUCCESS)) {
            throw new BusinessException(ErrorCode.TRIAL_NOT_ELIGIBLE, "The free trial is only for accounts that never had VIP.");
        }
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(SubscriptionPlan.CODE_VIP_MONTH)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "VIP plan is not configured."));
        LocalDateTime now = LocalDateTime.now();
        Subscription sub = subscriptionRepository.save(Subscription.builder()
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now)
                .expiresAt(now.plusDays(trialDays))
                .reminderSent(true)
                .trial(true)
                .build());
        auditLogService.log(AuditAction.TRIAL_STARTED, AuditResult.SUCCESS, userId, user.getEmail(), "Subscription",
                sub.getId().toString(), "days=" + trialDays);
        return paymentService.getMySubscription(userId);
    }
}
