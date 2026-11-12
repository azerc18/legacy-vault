package com.ltld.app.legacyvault.security.ratelimit;

import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Chặn một IP gọi quá nhiều lần các API công khai dưới /api/auth (đăng ký, đăng nhập, gửi OTP, quên mật khẩu...).
 * Vượt giới hạn: 429 + Retry-After, body cùng dạng ApiResponse với data.retryAfterSeconds (FE dịch theo errorCode).
 * Không áp cho link check-in DMS và xác nhận chéo: đó là bước mà nếu bị chặn có thể dẫn tới bàn giao khi Owner còn sống.
 */
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimiter limiter;
    private final ClientIpResolver ipResolver;
    private final AuditLogService auditLogService;

    public AuthRateLimitFilter(AuthRateLimiter limiter, ClientIpResolver ipResolver, AuditLogService auditLogService) {
        this.limiter = limiter;
        this.ipResolver = ipResolver;
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || limiter.ruleFor(path(request)) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rule = limiter.ruleFor(path(request));
        String ip = ipResolver.resolve(request);
        AuthRateLimiter.Decision decision = limiter.check(rule, ip);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        if (decision.firstRejection()) {
            log.warn("IP rate limit hit: rule={} ip={}", rule, ip);
            try {
                auditLogService.failure(AuditAction.IP_RATE_LIMITED, null, null, "rule=" + rule + " ip=" + ip);
            } catch (RuntimeException e) {
                log.warn("Could not write IP_RATE_LIMITED audit: {}", e.getMessage());
            }
        }
        writeTooManyRequests(response, decision.retryAfterSeconds());
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + ErrorCode.IP_RATE_LIMITED.name()
                + "\",\"message\":\"Too many requests. Please try again in " + retryAfterSeconds + " seconds.\""
                + ",\"data\":{\"retryAfterSeconds\":" + retryAfterSeconds + "}}");
    }
}
