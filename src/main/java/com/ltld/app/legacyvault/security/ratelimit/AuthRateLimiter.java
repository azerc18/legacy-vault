package com.ltld.app.legacyvault.security.ratelimit;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giữ các xô token trong bộ nhớ. Đủ dùng vì Web App chỉ chạy 1 instance; chạy nhiều instance thì mỗi instance
 * có giới hạn riêng (giới hạn thực tế nhân lên theo số instance), khi đó mới cần chuyển sang Redis.
 */
public class AuthRateLimiter {

    /** Kết quả kiểm: {@code retryAfterSeconds == 0} là cho qua. */
    public record Decision(String rule, long retryAfterSeconds, boolean firstRejection) {
        public boolean allowed() { return retryAfterSeconds == 0; }
    }

    private static final int SWEEP_EVERY = 1_000;

    private final Clock clock;
    private final int maxTrackedKeys;
    private final Map<String, String> ruleByPath = new HashMap<>();
    private final Map<String, RateLimitProperties.Rule> rules;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger callsSinceSweep = new AtomicInteger();

    public AuthRateLimiter(RateLimitProperties properties, Clock clock) {
        this.clock = clock;
        this.maxTrackedKeys = properties.getMaxTrackedKeys();
        this.rules = Map.copyOf(properties.getRules());
        rules.forEach((name, rule) -> {
            if (rule.getLimit() < 1 || rule.getPeriod() == null || rule.getPeriod().toMillis() < 1) {
                throw new IllegalStateException("app.rate-limit.rules." + name + ": limit >= 1 và period > 0");
            }
            for (String path : rule.getPaths()) {
                String previous = ruleByPath.put(path, name);
                if (previous != null) {
                    throw new IllegalStateException("app.rate-limit: " + path + " nằm trong cả " + previous + " và " + name);
                }
            }
        });
    }

    /** Quy tắc áp cho đường dẫn này, hoặc null nếu không giới hạn. */
    public String ruleFor(String path) {
        return path == null ? null : ruleByPath.get(path);
    }

    public Decision check(String rule, String clientIp) {
        RateLimitProperties.Rule config = rules.get(rule);
        long now = clock.millis();
        sweepIfNeeded(now);
        TokenBucket bucket = buckets.computeIfAbsent(rule + '|' + clientIp,
                k -> new TokenBucket(config.getLimit(), config.getPeriod().toMillis(), now));
        long wait = bucket.tryConsume(now);
        return new Decision(rule, wait, wait > 0 && bucket.markRejectionReported());
    }

    int trackedKeys() {
        return buckets.size();
    }

    // Xô đã hồi đầy thì bỏ được, để bộ nhớ không phình theo số IP từng gọi
    private void sweepIfNeeded(long now) {
        if (callsSinceSweep.incrementAndGet() < SWEEP_EVERY && buckets.size() < maxTrackedKeys) return;
        callsSinceSweep.set(0);
        buckets.entrySet().removeIf(e -> e.getValue().isFull(now));
    }
}
