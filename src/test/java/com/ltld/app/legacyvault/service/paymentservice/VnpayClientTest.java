package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VnpayClientTest {

    private VnpayClient vnpayClient;

    @BeforeEach
    void setUp() {
        vnpayClient = new VnpayClient(
                "TEST_TMN",
                "TEST_SECRET_KEY_1234567890_ABCDEF",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost:8084/api/payments/vnpay-return"
        );
    }

    @Test
    void buildPaymentUrl_containsScaledAmountAndValidSignature() {
        UUID txId = UUID.randomUUID();
        String url = vnpayClient.buildPaymentUrl(txId, 99_000L, "Upgrade VIP_MONTH", "127.0.0.1");

        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(url.contains("vnp_Amount=9900000"));
        assertTrue(url.contains("vnp_TxnRef=" + txId));
        assertTrue(url.contains("vnp_SecureHash="));
    }

    @Test
    void verify_returnsTrueForValidSignatureAndFalseWhenTampered() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", UUID.randomUUID().toString());
        params.put("vnp_TransactionNo", "14567890");

        String signature = vnpayClient.signParams(params);
        params.put("vnp_SecureHash", signature);

        assertTrue(vnpayClient.verify(params));

        // Tamper with amount -> signature must fail
        params.put("vnp_Amount", "100000");
        assertFalse(vnpayClient.verify(params));
    }

    @Test
    void unconfiguredClient_throwsServiceUnavailableOnCheckoutAndReturnsFalseOnVerify() {
        VnpayClient emptyClient = new VnpayClient("", "", "https://sandbox.vnpayment.vn", "");
        assertFalse(emptyClient.isConfigured());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> emptyClient.buildPaymentUrl(UUID.randomUUID(), 100_000L, "Test", "127.0.0.1")
        );
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());

        Map<String, String> params = Map.of("vnp_TxnRef", "abc", "vnp_SecureHash", "123");
        assertFalse(emptyClient.verify(params));

        BusinessException signEx = assertThrows(
                BusinessException.class,
                () -> emptyClient.signParams(params)
        );
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, signEx.getErrorCode());
    }
}
