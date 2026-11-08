package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaypalClientTest {

    private static final String BASE = "https://paypal.test";
    private static final PaypalProperties CONFIGURED = new PaypalProperties("id", "secret", BASE, 25_400L);

    private MockRestServiceServer server;
    private PaypalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PaypalClient(CONFIGURED, "https://legacyvault.codes", builder);
    }

    private void expectToken() {
        server.expect(requestTo(BASE + "/v1/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
    }

    @Test
    void toUsdCents_roundsToNearestCent() {
        assertThat(client.toUsdCents(99_000L)).isEqualTo(390L);
    }

    @Test
    void formatUsd_alwaysHasTwoDecimals() {
        assertThat(PaypalClient.formatUsd(390L)).isEqualTo("3.90");
        assertThat(PaypalClient.formatUsd(5L)).isEqualTo("0.05");
    }

    @Test
    void createOrder_sendsUsdAndCallbacks_returnsApprovalLink() {
        expectToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(jsonPath("$.intent").value("CAPTURE"))
                .andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("USD"))
                .andExpect(jsonPath("$.purchase_units[0].amount.value").value("3.90"))
                .andExpect(jsonPath("$.payment_source.paypal.experience_context.return_url")
                        .value("https://legacyvault.codes/api/payments/paypal-return"))
                .andExpect(jsonPath("$.payment_source.paypal.experience_context.cancel_url")
                        .value("https://legacyvault.codes/api/payments/paypal-cancel"))
                .andRespond(withSuccess("""
                        {"id":"ORDER1","status":"PAYER_ACTION_REQUIRED",
                         "links":[{"rel":"self","href":"https://x/self"},
                                  {"rel":"payer-action","href":"https://x/approve"}]}
                        """, MediaType.APPLICATION_JSON));

        PaypalClient.Order order = client.createOrder(UUID.randomUUID(), 390L, "LegacyVault VIP_MONTH");

        assertThat(order.id()).isEqualTo("ORDER1");
        assertThat(order.approveUrl()).isEqualTo("https://x/approve");
        server.verify();
    }

    @Test
    void createOrder_noApprovalLink_throwsServiceUnavailable() {
        expectToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders"))
                .andRespond(withSuccess("{\"id\":\"ORDER1\",\"links\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createOrder(UUID.randomUUID(), 390L, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void createOrder_notConfigured_throwsServiceUnavailable() {
        PaypalClient unconfigured = new PaypalClient(new PaypalProperties("", "", null, 0L), "http://localhost:5173");

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.createOrder(UUID.randomUUID(), 390L, "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void capture_completed_parsesAmountAndSendsIdempotencyKey() {
        expectToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORDER1/capture"))
                .andExpect(header("PayPal-Request-Id", "capture-ORDER1"))
                .andRespond(withSuccess("""
                        {"id":"ORDER1","status":"COMPLETED","purchase_units":[{"payments":{"captures":[
                          {"id":"CAP1","status":"COMPLETED","amount":{"currency_code":"USD","value":"3.90"}}]}}]}
                        """, MediaType.APPLICATION_JSON));

        PaypalClient.Capture capture = client.capture("ORDER1");

        assertThat(capture.completed()).isTrue();
        assertThat(capture.captureId()).isEqualTo("CAP1");
        assertThat(capture.currency()).isEqualTo("USD");
        assertThat(capture.cents()).isEqualTo(390L);
        server.verify();
    }

    @Test
    void capture_pending_isNotCompleted() {
        expectToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORDER1/capture"))
                .andRespond(withSuccess("""
                        {"status":"COMPLETED","purchase_units":[{"payments":{"captures":[
                          {"id":"CAP1","status":"PENDING","amount":{"currency_code":"USD","value":"3.90"}}]}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.capture("ORDER1").completed()).isFalse();
    }

    @Test
    void capture_rejected422_isDeclinedNotThrown() {
        expectToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORDER1/capture"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

        PaypalClient.Capture capture = client.capture("ORDER1");

        assertThat(capture.completed()).isFalse();
        assertThat(capture.status()).isEqualTo("HTTP_422");
    }

    @Test
    void capture_serverError_throwsSoCallerKeepsPending() {
        expectToken();
        server.expect(requestTo(BASE + "/v2/checkout/orders/ORDER1/capture"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.capture("ORDER1")).isInstanceOf(HttpServerErrorException.class);
    }
}
