package com.ltld.app.legacyvault.service.captchaservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Cloudflare Turnstile: FE lấy token từ widget, BE gửi token + secret lên siteverify.
 * Token dùng một lần và hết hạn sau 300 giây (Cloudflare tự kiểm).
 */
@Slf4j
@Service
@EnableConfigurationProperties(CaptchaProperties.class)
public class TurnstileCaptchaService implements CaptchaService {

    private record SiteverifyResponse(boolean success, String hostname,
                                      @JsonProperty("error-codes") List<String> errorCodes) {}

    private final CaptchaProperties properties;
    private final AuditLogService auditLogService;
    private final RestClient client;
    private final boolean enabled;

    @Autowired
    public TurnstileCaptchaService(CaptchaProperties properties, AuditLogService auditLogService) {
        this(properties, auditLogService, RestClient.builder().requestFactory(timeouts(properties)));
    }

    TurnstileCaptchaService(CaptchaProperties properties, AuditLogService auditLogService, RestClient.Builder builder) {
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.client = builder.build();
        boolean keysPresent = !isBlank(properties.getSiteKey()) && !isBlank(properties.getSecretKey());
        if (properties.isEnabled() && !keysPresent) {
            // Bật mà thiếu key thì không chặn ai (nếu chặn sẽ không ai đăng ký được), nhưng báo to trong log
            log.error("APP_CAPTCHA_ENABLED=true nhưng thiếu APP_CAPTCHA_SITE_KEY / APP_CAPTCHA_SECRET_KEY: CAPTCHA đang TẮT.");
        }
        this.enabled = properties.isEnabled() && keysPresent;
    }

    private static SimpleClientHttpRequestFactory timeouts(CaptchaProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        return factory;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String siteKey() {
        return enabled ? properties.getSiteKey().trim() : null;
    }

    @Override
    public int loginAfterFailures() {
        return Math.max(1, properties.getLoginAfterFailures());
    }

    @Override
    public Result verify(String token) {
        if (isBlank(token)) return Result.FAILED;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.getSecretKey().trim());
        form.add("response", token.trim());
        try {
            SiteverifyResponse res = client.post().uri(properties.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteverifyResponse.class);
            if (res == null) return Result.UNAVAILABLE;
            if (res.success()) {
                if (!hostnameAllowed(res.hostname())) {
                    log.warn("Turnstile token solved on unexpected hostname: {}", res.hostname());
                    return Result.FAILED;
                }
                return Result.PASSED;
            }
            List<String> codes = res.errorCodes() == null ? List.of() : res.errorCodes();
            // Lỗi phía mình (secret sai / thiếu) hoặc lỗi nội bộ của Cloudflare: không phải lỗi người dùng
            if (codes.contains("invalid-input-secret") || codes.contains("missing-input-secret")
                    || codes.contains("internal-error")) {
                log.error("Turnstile siteverify rejected the request: {}", codes);
                return Result.UNAVAILABLE;
            }
            return Result.FAILED;
        } catch (RestClientException e) {
            log.warn("Turnstile siteverify unreachable: {}", e.getMessage());
            return Result.UNAVAILABLE;
        }
    }

    @Override
    public void requirePassed(String token, String step) {
        if (!enabled) return;
        Result result = verify(token);
        switch (result) {
            case PASSED -> { }
            case FAILED -> {
                auditLogService.failure(AuditAction.CAPTCHA_FAILED, null, null, "step=" + step);
                throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "Please complete the security check and try again.");
            }
            case UNAVAILABLE -> {
                auditLogService.failure(AuditAction.CAPTCHA_UNAVAILABLE, null, null, "step=" + step + " (blocked)");
                throw new BusinessException(ErrorCode.CAPTCHA_UNAVAILABLE,
                        "The security check is temporarily unavailable. Please try again in a few minutes.");
            }
        }
    }

    private boolean hostnameAllowed(String hostname) {
        List<String> allowed = properties.getAllowedHostnames();
        if (allowed == null || allowed.stream().allMatch(TurnstileCaptchaService::isBlank)) return true;
        return hostname != null && allowed.stream().anyMatch(h -> h != null && h.trim().equalsIgnoreCase(hostname));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
