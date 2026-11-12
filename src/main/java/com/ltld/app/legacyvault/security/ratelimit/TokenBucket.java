package com.ltld.app.legacyvault.security.ratelimit;

/**
 * Xô token cho một cặp (quy tắc, IP): chứa tối đa {@code capacity} lượt, hồi dần đều {@code capacity} lượt mỗi chu kỳ.
 * Thời gian tính bằng mili giây để test truyền đồng hồ giả.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerMilli;
    private double tokens;
    private long lastRefillMillis;
    /** Đã ghi audit cho lần bị chặn hiện tại chưa (reset khi lại cho qua). */
    private boolean rejectionReported;

    TokenBucket(int capacity, long periodMillis, long nowMillis) {
        this.capacity = capacity;
        this.refillPerMilli = (double) capacity / periodMillis;
        this.tokens = capacity;
        this.lastRefillMillis = nowMillis;
    }

    /** Lấy 1 lượt. Trả 0 nếu được phép, ngược lại số giây phải chờ tới khi có lượt (>= 1). */
    synchronized long tryConsume(long nowMillis) {
        refill(nowMillis);
        if (tokens >= 1) {
            tokens -= 1;
            rejectionReported = false;
            return 0;
        }
        long waitMillis = (long) Math.ceil((1 - tokens) / refillPerMilli);
        return Math.max(1, (waitMillis + 999) / 1000);
    }

    /** true ở lần bị chặn đầu tiên của một đợt, để audit không bị ghi mỗi request khi đang bị spam. */
    synchronized boolean markRejectionReported() {
        if (rejectionReported) return false;
        rejectionReported = true;
        return true;
    }

    /** Xô đã hồi đầy: xoá đi không làm thay đổi kết quả, vì xô mới cũng đầy. */
    synchronized boolean isFull(long nowMillis) {
        refill(nowMillis);
        return tokens >= capacity;
    }

    private void refill(long nowMillis) {
        long elapsed = nowMillis - lastRefillMillis;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerMilli);
            lastRefillMillis = nowMillis;
        }
    }
}
