package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentTransactionResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    CheckoutResponse checkout(UUID userId, CheckoutRequest request, String ipAddress);

    Map<String, String> handleVnpayIpn(Map<String, String> params);

    String handleVnpayReturn(Map<String, String> params);

    SubscriptionMeResponse getMySubscription(UUID userId);

    List<PaymentTransactionResponse> getMyPayments(UUID userId);
}

