package com.ltld.app.legacyvault.dto.systempolicydto;

/**
 * Chính sách Dead Man's Switch đang có hiệu lực: khoảng cho phép + giá trị gợi ý cho form của Owner,
 * và số ngày chờ trước khi chuyển sang Executor dự phòng (BR-03) cho cấu hình DMS mới.
 * Owner cũng đọc được (GET /api/dms/policy) để form hiện đúng giới hạn.
 */
public record DmsPolicy(Range checkinIntervalDays,
                        Range gracePeriodDays,
                        Range reminderCount,
                        int backupActivationWaitDays) {

    /** Giới hạn cứng = @Min/@Max của DmsConfigRequest. Admin chỉ thu hẹp được trong khoảng này. */
    public static final Range HARD_CHECKIN_INTERVAL = new Range(1, 30, 365);
    public static final Range HARD_GRACE_PERIOD = new Range(1, 7, 90);
    public static final Range HARD_REMINDER_COUNT = new Range(1, 3, 10);
    public static final int HARD_BACKUP_WAIT_MIN = 1;
    public static final int HARD_BACKUP_WAIT_MAX = 90;

    /** Giá trị trước khi có trang này (và khi không đọc được DB): giữ đúng hành vi cũ. */
    public static final DmsPolicy DEFAULTS =
            new DmsPolicy(HARD_CHECKIN_INTERVAL, HARD_GRACE_PERIOD, HARD_REMINDER_COUNT, 14);

    public record Range(int min, int defaultValue, int max) {
        public boolean contains(int value) {
            return value >= min && value <= max;
        }
    }
}
