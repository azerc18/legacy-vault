package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// m5: cấu hình VNPay gom vào VnpayProperties (app.vnpay.*); thiếu secret thì app vẫn khởi động
@ExtendWith(OutputCaptureExtension.class)
class VnpayPropertiesStartupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(VnpayClient.class)
            .withPropertyValues("app.frontend.base-url=http://localhost:5173");

    @Test
    void missingSecrets_contextStarts_warnsOnce_andCheckoutFailsWithServiceUnavailable(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            VnpayClient client = context.getBean(VnpayClient.class);
            assertThat(client.isConfigured()).isFalse();

            assertThatThrownBy(() -> client.buildPaymentUrl(UUID.randomUUID(), 99_000L, "Upgrade", "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("VNPay payment gateway is not configured")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        });
        assertThat(output.getOut().split("VNPay chưa cấu hình", -1)).hasSize(2);   // đúng 1 lần
    }

    @Test
    void appVnpayKeys_areBoundToProperties() {
        runner.withPropertyValues(
                        "app.vnpay.tmn-code= TMN01 ",
                        "app.vnpay.hash-secret=SECRET",
                        "app.vnpay.pay-url=https://pay.example/vpcpay.html",
                        "app.vnpay.return-url=https://api.example/api/payments/vnpay-return",
                        "app.vnpay.activate-on-return=true")
                .run(context -> {
                    VnpayProperties props = context.getBean(VnpayProperties.class);
                    assertThat(props.tmnCode()).isEqualTo("TMN01");
                    assertThat(props.hashSecret()).isEqualTo("SECRET");
                    assertThat(props.payUrl()).isEqualTo("https://pay.example/vpcpay.html");
                    assertThat(props.returnUrl()).isEqualTo("https://api.example/api/payments/vnpay-return");
                    assertThat(props.activateOnReturn()).isTrue();

                    String url = context.getBean(VnpayClient.class)
                            .buildPaymentUrl(UUID.randomUUID(), 99_000L, "Upgrade", "127.0.0.1");
                    assertThat(url).startsWith("https://pay.example/vpcpay.html?")
                            .contains("vnp_TmnCode=TMN01")
                            .contains("vnp_ReturnUrl=https%3A%2F%2Fapi.example%2Fapi%2Fpayments%2Fvnpay-return");
                });
    }

    @Test
    void missingKeys_useSandboxPayUrlAndActivateOnReturnFalse() {
        runner.run(context -> {
            VnpayProperties props = context.getBean(VnpayProperties.class);
            assertThat(props.payUrl()).isEqualTo(VnpayProperties.SANDBOX_PAY_URL);
            assertThat(props.activateOnReturn()).isFalse();
        });
    }
}
