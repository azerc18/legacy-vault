package com.ltld.app.legacyvault.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    /** Đồng hồ chỉnh tay được. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-12T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();

    private static RateLimitProperties.Rule rule(int limit, Duration period, String... paths) {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setLimit(limit);
        rule.setPeriod(period);
        rule.setPaths(List.of(paths));
        return rule;
    }

    private AuthRateLimiter limiter(int maxTrackedKeys) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxTrackedKeys(maxTrackedKeys);
        properties.setRules(Map.of(
                "register", rule(3, Duration.ofHours(1), "/api/auth/register"),
                "login", rule(5, Duration.ofMinutes(1), "/api/auth/login", "/api/auth/login/2fa")));
        return new AuthRateLimiter(properties, clock);
    }

    @Test
    void allowsUpToLimit_thenRejectsWithRetryAfter() {
        AuthRateLimiter limiter = limiter(1000);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.check("register", "1.1.1.1").allowed()).isTrue();
        }
        AuthRateLimiter.Decision rejected = limiter.check("register", "1.1.1.1");
        assertThat(rejected.allowed()).isFalse();
        // 3 lượt/giờ -> 1 lượt hồi sau 20 phút
        assertThat(rejected.retryAfterSeconds()).isEqualTo(20 * 60);
    }

    @Test
    void tokensRefillGradually() {
        AuthRateLimiter limiter = limiter(1000);
        for (int i = 0; i < 3; i++) limiter.check("register", "1.1.1.1");
        clock.advance(Duration.ofMinutes(19));
        assertThat(limiter.check("register", "1.1.1.1").allowed()).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.check("register", "1.1.1.1").allowed()).isTrue();
        assertThat(limiter.check("register", "1.1.1.1").allowed()).isFalse();
    }

    @Test
    void ipsAndRulesHaveSeparateBuckets() {
        AuthRateLimiter limiter = limiter(1000);
        for (int i = 0; i < 3; i++) limiter.check("register", "1.1.1.1");
        assertThat(limiter.check("register", "1.1.1.1").allowed()).isFalse();
        assertThat(limiter.check("register", "2.2.2.2").allowed()).isTrue();
        assertThat(limiter.check("login", "1.1.1.1").allowed()).isTrue();
    }

    @Test
    void firstRejection_reportedOncePerBlockedStreak() {
        AuthRateLimiter limiter = limiter(1000);
        for (int i = 0; i < 3; i++) limiter.check("register", "1.1.1.1");
        assertThat(limiter.check("register", "1.1.1.1").firstRejection()).isTrue();
        assertThat(limiter.check("register", "1.1.1.1").firstRejection()).isFalse();
        clock.advance(Duration.ofMinutes(20));
        assertThat(limiter.check("register", "1.1.1.1").allowed()).isTrue();
        assertThat(limiter.check("register", "1.1.1.1").firstRejection()).isTrue();
    }

    @Test
    void pathMapping_onlyConfiguredPaths_neverDmsLinks() {
        AuthRateLimiter limiter = limiter(1000);
        assertThat(limiter.ruleFor("/api/auth/login/2fa")).isEqualTo("login");
        assertThat(limiter.ruleFor("/api/auth/refresh")).isNull();
        assertThat(limiter.ruleFor("/api/dms/checkin-link")).isNull();
        assertThat(limiter.ruleFor("/api/dms/confirm")).isNull();
    }

    @Test
    void fullBucketsAreSwept_soMemoryDoesNotGrowWithEveryIp() {
        AuthRateLimiter limiter = limiter(10);
        for (int i = 0; i < 10; i++) limiter.check("login", "10.0.0." + i);
        clock.advance(Duration.ofMinutes(2));   // mọi xô đã hồi đầy
        limiter.check("login", "10.0.1.1");
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void samePathInTwoRules_failsFast() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRules(Map.of(
                "a", rule(1, Duration.ofMinutes(1), "/api/auth/login"),
                "b", rule(1, Duration.ofMinutes(1), "/api/auth/login")));
        assertThatThrownBy(() -> new AuthRateLimiter(properties, clock)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void overCap_withBucketsStillInUse_evictsDownBelowCap() {
        // Nhiều IP cùng đang dùng dở lượt: quét xô đầy không bỏ được gì, vẫn phải giữ map dưới trần
        AuthRateLimiter limiter = limiter(10);
        for (int i = 0; i < 25; i++) {
            limiter.check("register", "10.0.0." + i);
            clock.advance(Duration.ofMillis(600));   // xô register 1 giờ chưa kịp hồi đầy
        }
        assertThat(limiter.trackedKeys()).isLessThan(20);
    }

    @Test
    void sweep_isThrottled_toOncePerSecond() {
        AuthRateLimiter limiter = limiter(10);
        for (int i = 0; i < 10; i++) limiter.check("register", "10.0.0." + i);
        limiter.check("register", "10.0.1.1");   // vượt trần -> quét, bỏ bớt
        int afterFirstSweep = limiter.trackedKeys();
        // Cùng một mili giây: không quét lại dù vẫn chạm trần, map chỉ tạm lớn thêm
        for (int i = 0; i < 5; i++) limiter.check("register", "10.0.2." + i);
        assertThat(limiter.trackedKeys()).isEqualTo(afterFirstSweep + 5);
        clock.advance(Duration.ofSeconds(1));
        limiter.check("register", "10.0.3.1");
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(10);
    }
}
