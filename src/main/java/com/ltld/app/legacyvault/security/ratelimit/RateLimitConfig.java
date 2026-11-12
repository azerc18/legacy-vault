package com.ltld.app.legacyvault.security.ratelimit;

import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

/**
 * Đăng ký filter giới hạn theo IP. Filter không phải @Component để các test @WebMvcTest không tự nạp nó.
 * Chạy trước Spring Security, để request bị chặn không tốn công giải mã JWT hay chạm DB.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public AuthRateLimiter authRateLimiter(RateLimitProperties properties, Clock clock) {
        return new AuthRateLimiter(properties, clock);
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(
            RateLimitProperties properties, AuthRateLimiter limiter, AuditLogService auditLogService) {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                limiter, new ClientIpResolver(properties.isTrustForwardedFor()), auditLogService);
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/auth/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setEnabled(properties.isEnabled());
        registration.setName("authRateLimitFilter");
        return registration;
    }
}
