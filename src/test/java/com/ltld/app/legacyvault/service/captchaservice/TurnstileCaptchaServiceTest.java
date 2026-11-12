package com.ltld.app.legacyvault.service.captchaservice;

import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileCaptchaServiceTest {

    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final AuditLogService audit = mock(AuditLogService.class);
    private MockRestServiceServer server;

    private TurnstileCaptchaService service(boolean enabled, String siteKey, String secret) {
        return service(enabled, siteKey, secret, java.util.List.of());
    }

    private TurnstileCaptchaService service(boolean enabled, String siteKey, String secret, java.util.List<String> hosts) {
        CaptchaProperties p = new CaptchaProperties();
        p.setAllowedHostnames(hosts);
        p.setEnabled(enabled);
        p.setSiteKey(siteKey);
        p.setSecretKey(secret);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new TurnstileCaptchaService(p, audit, builder);
    }

    private TurnstileCaptchaService enabled() {
        return service(true, "site-key", "secret-key");
    }

    private void respond(String json) {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("secret", "secret-key", "response", "tok")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void disabledByDefault_orWhenKeysMissing() {
        assertThat(service(false, "site-key", "secret-key").isEnabled()).isFalse();
        // Bật mà thiếu secret: coi như tắt để không chặn mọi người đăng ký
        TurnstileCaptchaService missingSecret = service(true, "site-key", "");
        assertThat(missingSecret.isEnabled()).isFalse();
        assertThat(missingSecret.siteKey()).isNull();
        assertThatCode(() -> missingSecret.requirePassed(null, "register")).doesNotThrowAnyException();
        verifyNoInteractions(audit);
    }

    @Test
    void success_passes() {
        TurnstileCaptchaService s = enabled();
        respond("{\"success\":true,\"error-codes\":[]}");
        assertThat(s.verify("tok")).isEqualTo(CaptchaService.Result.PASSED);
        server.verify();
    }

    @Test
    void blankToken_fails_withoutCallingCloudflare() {
        TurnstileCaptchaService s = enabled();
        assertThat(s.verify("  ")).isEqualTo(CaptchaService.Result.FAILED);
        server.verify();   // không có request nào
    }

    @Test
    void rejectedToken_fails() {
        TurnstileCaptchaService s = enabled();
        respond("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}");
        assertThat(s.verify("tok")).isEqualTo(CaptchaService.Result.FAILED);
    }

    @Test
    void wrongSecret_isOurFault_notTheUsers() {
        TurnstileCaptchaService s = enabled();
        respond("{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}");
        assertThat(s.verify("tok")).isEqualTo(CaptchaService.Result.UNAVAILABLE);
    }

    @Test
    void serverError_isUnavailable() {
        TurnstileCaptchaService s = enabled();
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(s.verify("tok")).isEqualTo(CaptchaService.Result.UNAVAILABLE);
    }

    @Test
    void requirePassed_failClosed_onRejectedToken() {
        TurnstileCaptchaService s = enabled();
        respond("{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}");
        assertThatThrownBy(() -> s.requirePassed("tok", "register"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_INVALID);
        verify(audit).failure(AuditAction.CAPTCHA_FAILED, null, null, "step=register");
    }

    @Test
    void requirePassed_failClosed_whenCloudflareDown() {
        TurnstileCaptchaService s = enabled();
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> s.requirePassed("tok", "forgot-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_UNAVAILABLE);
        verify(audit).failure(org.mockito.ArgumentMatchers.eq(AuditAction.CAPTCHA_UNAVAILABLE),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), anyString());
    }

    @Test
    void tokenSolvedOnAnotherSite_fails_whenHostnamesConfigured() {
        TurnstileCaptchaService s = service(true, "site-key", "secret-key", java.util.List.of("legacyvault.codes"));
        respond("{\"success\":true,\"hostname\":\"evil.example\",\"error-codes\":[]}");
        assertThat(s.verify("tok")).isEqualTo(CaptchaService.Result.FAILED);
    }

    @Test
    void tokenSolvedOnOurSite_passes_caseInsensitive() {
        TurnstileCaptchaService s = service(true, "site-key", "secret-key", java.util.List.of("legacyvault.codes"));
        respond("{\"success\":true,\"hostname\":\"LegacyVault.codes\",\"error-codes\":[]}");
        assertThat(s.verify("tok")).isEqualTo(CaptchaService.Result.PASSED);
    }
}
