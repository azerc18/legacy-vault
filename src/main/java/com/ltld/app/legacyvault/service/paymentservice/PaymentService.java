package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentTransactionResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    CheckoutResponse checkout(UUID userId, CheckoutRequest request, String ipAddress);

    Map<String, String> handleVnpayIpn(Map<String, String> params);

    String handleVnpayReturn(Map<String, String> params);

    /** Trả về URL frontend để redirect người mua; chỉ PayPal capture thành công mới kích hoạt gói. */
    String handlePaypalReturn(String orderId);

    String handlePaypalCancel(String orderId);

    /** Các cổng đã cấu hình khóa; cổng chưa cấu hình thì ẩn. */
    List<String> getAvailableProviders();

    SubscriptionMeResponse getMySubscription(UUID userId);

    List<PaymentTransactionResponse> getMyPayments(UUID userId);

    /** Đối soát một đơn PENDING quá hạn với cổng thanh toán (job {@link PendingPaymentReconciler}). */
    ReconcileOutcome reconcilePending(UUID transactionId, LocalDateTime now, Duration giveUpAfter);
}

