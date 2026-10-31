package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentTransactionResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionPlanDto;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.PaymentException;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * T1: Xử lý thanh toán VNPay, IPN idempotent, kích hoạt/gia hạn gói VIP.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EntitlementServiceImpl entitlementService;
    private final VnpayClient vnpayClient;
    private final AuditLogService auditLogService;

    @Value("${APP_FRONTEND_URL:http://localhost:5173}")
    private String frontendBaseUrl = "http://localhost:5173";

    @Override
    @Transactional
    public CheckoutResponse checkout(UUID userId, CheckoutRequest request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found.", HttpStatus.NOT_FOUND));

        String code = request.getPlanCode().trim().toUpperCase();
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("Subscription plan not found: " + code, HttpStatus.BAD_REQUEST));

        if (plan.getPriceVnd() <= 0) {
            throw new BusinessException("Cannot checkout a free plan.", HttpStatus.BAD_REQUEST);
        }

        PaymentTransaction tx = PaymentTransaction.builder()
                .user(user)
                .plan(plan)
                .amountVnd(plan.getPriceVnd())
                .status(PaymentStatus.PENDING)
                .build();
        tx = paymentTransactionRepository.save(tx);

        String orderInfo = "Upgrade LegacyVault " + plan.getCode() + " - " + tx.getId();
        String paymentUrl = vnpayClient.buildPaymentUrl(tx.getId(), plan.getPriceVnd(), orderInfo, ipAddress);

        return CheckoutResponse.builder()
                .transactionId(tx.getId())
                .paymentUrl(paymentUrl)
                .planCode(plan.getCode())
                .amountVnd(plan.getPriceVnd())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        if (!vnpayClient.verify(params)) {
            throw PaymentException.signatureInvalid();
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        UUID txId;
        try {
            txId = UUID.fromString(txnRef.trim());
        } catch (IllegalArgumentException e) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        Optional<PaymentTransaction> txOpt = paymentTransactionRepository.findByIdForUpdate(txId);
        if (txOpt.isEmpty()) {
            txOpt = paymentTransactionRepository.findById(txId);
        }
        if (txOpt.isEmpty()) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        PaymentTransaction tx = txOpt.get();

        // Idempotency: nếu giao dịch đã xử lý thành công thì trả RspCode=02 và không kích hoạt lại
        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        long vnpAmountVnd = parseVnpAmountVnd(params.get("vnp_Amount"));
        if (vnpAmountVnd != tx.getAmountVnd()) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setVnpResponseCode(params.getOrDefault("vnp_ResponseCode", "04"));
            paymentTransactionRepository.save(tx);

            User user = tx.getUser();
            auditLogService.log(
                    AuditAction.PAYMENT_FAILED,
                    AuditResult.FAILURE,
                    user != null ? user.getId() : null,
                    user != null ? user.getEmail() : null,
                    "PaymentTransaction",
                    tx.getId().toString(),
                    "Amount mismatch: expected " + tx.getAmountVnd() + " VND, got " + vnpAmountVnd + " VND"
            );
            throw PaymentException.amountMismatch();
        }

        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        tx.setVnpResponseCode(responseCode);
        tx.setVnpTransactionNo(transactionNo);
        tx.setVnpBankCode(bankCode);

        User user = tx.getUser();
        SubscriptionPlan plan = tx.getPlan();

        if ("00".equals(responseCode)) {
            LocalDateTime now = LocalDateTime.now();
            tx.setStatus(PaymentStatus.SUCCESS);
            tx.setPaidAt(now);
            paymentTransactionRepository.save(tx);

            activateOrExtendSubscription(user, plan, now);

            auditLogService.log(
                    AuditAction.PAYMENT_SUCCEEDED,
                    AuditResult.SUCCESS,
                    user != null ? user.getId() : null,
                    user != null ? user.getEmail() : null,
                    "PaymentTransaction",
                    tx.getId().toString(),
                    plan != null ? plan.getCode() : "VIP"
            );
        } else {
            tx.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(tx);

            auditLogService.log(
                    AuditAction.PAYMENT_FAILED,
                    AuditResult.FAILURE,
                    user != null ? user.getId() : null,
                    user != null ? user.getEmail() : null,
                    "PaymentTransaction",
                    tx.getId().toString(),
                    "vnp_ResponseCode=" + responseCode
            );
        }

        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    @Override
    @Transactional(noRollbackFor = PaymentException.class)
    public String handleVnpayReturn(Map<String, String> params) {
        if (!vnpayClient.verify(params)) {
            throw PaymentException.signatureInvalid();
        }
        String txnRef = params.getOrDefault("vnp_TxnRef", "");
        String responseCode = params.getOrDefault("vnp_ResponseCode", "");

        try {
            handleVnpayIpn(params);
        } catch (PaymentException ex) {
            return buildFrontendResultUrl("failed", txnRef, ex.getErrorCode());
        }

        String status = "00".equals(responseCode) ? "success" : "failed";
        return buildFrontendResultUrl(status, txnRef, null);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionMeResponse getMySubscription(UUID userId) {
        List<SubscriptionPlanDto> plans = subscriptionPlanRepository.findAllByOrderByPriceVndAsc().stream()
                .map(SubscriptionPlanDto::from)
                .toList();

        Optional<Subscription> activeSubOpt = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE);

        if (activeSubOpt.isPresent()) {
            Subscription sub = activeSubOpt.get();
            if (sub.getExpiresAt() == null || sub.getExpiresAt().isAfter(LocalDateTime.now())) {
                SubscriptionPlan plan = sub.getPlan();
                return SubscriptionMeResponse.builder()
                        .planCode(plan.getCode())
                        .planName(plan.getName())
                        .status(sub.getStatus().name())
                        .startedAt(sub.getStartedAt())
                        .expiresAt(sub.getExpiresAt())
                        .maxVaults(plan.getMaxVaults())
                        .maxBeneficiariesPerVault(plan.getMaxBeneficiaries())
                        .maxStorageBytes(plan.getStorageQuotaBytes())
                        .legacyMessageEnabled(plan.isLegacyMessagesEnabled())
                        .vip(plan.isVip())
                        .availablePlans(plans)
                        .build();
            }
        }

        Optional<Subscription> latestSubOpt = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        SubscriptionPlan freePlan = entitlementService.resolveEffectivePlan(userId);
        String currentStatus = latestSubOpt.map(s -> s.getStatus().name()).orElse(SubscriptionStatus.ACTIVE.name());
        LocalDateTime expiresAt = latestSubOpt.map(Subscription::getExpiresAt).orElse(null);

        return SubscriptionMeResponse.builder()
                .planCode(freePlan.getCode())
                .planName(freePlan.getName())
                .status(currentStatus)
                .startedAt(latestSubOpt.map(Subscription::getStartedAt).orElse(null))
                .expiresAt(expiresAt)
                .maxVaults(freePlan.getMaxVaults())
                .maxBeneficiariesPerVault(freePlan.getMaxBeneficiaries())
                .maxStorageBytes(freePlan.getStorageQuotaBytes())
                .legacyMessageEnabled(freePlan.isLegacyMessagesEnabled())
                .vip(false)
                .availablePlans(plans)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getMyPayments(UUID userId) {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PaymentTransactionResponse::from)
                .toList();
    }

    private void activateOrExtendSubscription(User user, SubscriptionPlan plan, LocalDateTime now) {
        if (user == null || plan == null) {
            return;
        }
        int durationDays = (plan.getDurationDays() != null && plan.getDurationDays() > 0)
                ? plan.getDurationDays()
                : 30;
        Optional<Subscription> activeOpt = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(user.getId(), SubscriptionStatus.ACTIVE);

        if (activeOpt.isPresent()) {
            Subscription existing = activeOpt.get();
            LocalDateTime baseTime = (existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(now))
                    ? existing.getExpiresAt()
                    : now;
            existing.setPlan(plan);
            existing.setStatus(SubscriptionStatus.ACTIVE);
            existing.setReminderSent(false);
            existing.setExpiresAt(baseTime.plusDays(durationDays));
            subscriptionRepository.save(existing);
        } else {
            Subscription created = Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(now)
                    .expiresAt(now.plusDays(durationDays))
                    .reminderSent(false)
                    .build();
            subscriptionRepository.save(created);
        }
    }

    private long parseVnpAmountVnd(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(rawAmount.trim()) / 100L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String buildFrontendResultUrl(String status, String txId, String errorCode) {
        String base = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? ""
                : frontendBaseUrl.replaceAll("/+$", "");
        StringBuilder sb = new StringBuilder(base)
                .append("/payment/result?status=").append(status);
        if (txId != null && !txId.isBlank()) {
            sb.append("&txId=").append(txId);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            sb.append("&error=").append(errorCode);
        }
        return sb.toString();
    }
}
