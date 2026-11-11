package com.ltld.app.legacyvault.service.paymentservice;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/** Việc 3: hoàn tiền VNPay (vnp_Command=refund) ký đúng thứ tự trường và chỉ ok khi phản hồi có chữ ký đúng. */
class VnpayRefundTest {

    private static final String SECRET = "SECRET_KEY_FOR_TESTING_1234567890";
    private final UUID txId = UUID.randomUUID();
    private final VnpayClient client = spy(new VnpayClient(new VnpayProperties("DEMO_TMN", SECRET,
            VnpayProperties.SANDBOX_PAY_URL, "http://localhost:8084/api/payments/vnpay-return", false), "http://localhost:5173"));

    private Map<String, Object> response(String code, boolean signed) {
        Map<String, Object> r = new HashMap<>();
        r.put("vnp_ResponseId", "r1");
        r.put("vnp_Command", "refund");
        r.put("vnp_ResponseCode", code);
        r.put("vnp_Message", "Refund success");
        r.put("vnp_TmnCode", "DEMO_TMN");
        r.put("vnp_TxnRef", VnpayClient.toTxnRef(txId));
        r.put("vnp_Amount", "9900000");
        r.put("vnp_BankCode", "NCB");
        r.put("vnp_PayDate", "20261111120000");
        r.put("vnp_TransactionNo", "14000099");
        r.put("vnp_TransactionType", "02");
        r.put("vnp_TransactionStatus", "05");
        r.put("vnp_OrderInfo", "Hoan tien");
        String data = String.join("|", "r1", "refund", code, "Refund success", "DEMO_TMN", VnpayClient.toTxnRef(txId),
                "9900000", "NCB", "20261111120000", "14000099", "02", "05", "Hoan tien");
        r.put("vnp_SecureHash", signed ? VnpayClient.hmacSha512(SECRET, data) : "deadbeef");
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void refund_signsRequestAndAcceptsSignedSuccess() {
        ArgumentCaptor<Map<String, String>> body = ArgumentCaptor.forClass(Map.class);
        doReturn(response("00", true)).when(client).postQuery(body.capture());

        VnpayClient.RefundResult r = client.refund(txId, 99_000, "14000001", LocalDateTime.now().minusDays(1), "admin@lv.local");

        assertThat(r.ok()).isTrue();
        assertThat(r.transactionNo()).isEqualTo("14000099");
        Map<String, String> b = body.getValue();
        assertThat(b.get("vnp_Command")).isEqualTo("refund");
        assertThat(b.get("vnp_TransactionType")).isEqualTo("02");
        assertThat(b.get("vnp_Amount")).isEqualTo("9900000");
        String data = String.join("|", b.get("vnp_RequestId"), "2.1.0", "refund", "DEMO_TMN", "02", b.get("vnp_TxnRef"),
                "9900000", "14000001", b.get("vnp_TransactionDate"), "admin@lv.local", b.get("vnp_CreateDate"),
                b.get("vnp_IpAddr"), b.get("vnp_OrderInfo"));
        assertThat(b.get("vnp_SecureHash")).isEqualTo(VnpayClient.hmacSha512(SECRET, data));
    }

    @Test
    void refund_forgedResponse_isNotOk() {
        doReturn(response("00", false)).when(client).postQuery(org.mockito.ArgumentMatchers.any());

        assertThat(client.refund(txId, 99_000, "1", LocalDateTime.now(), "a").ok()).isFalse();
    }

    @Test
    void refund_networkError_hasNoResponseCode() {
        doThrow(new RuntimeException("timeout")).when(client).postQuery(org.mockito.ArgumentMatchers.any());

        VnpayClient.RefundResult r = client.refund(txId, 99_000, "1", LocalDateTime.now(), "a");
        assertThat(r.ok()).isFalse();
        assertThat(r.responseCode()).isNull();
    }
}
