package com.ltld.app.legacyvault.exception;

import lombok.Getter;

/**
 * F2: lỗi check-in một chạm từ link email, kèm {@link Reason} để trang FE hiện đúng câu (m5).
 * errors.json chỉ có CHECKIN_TOKEN_INVALID, nên các trường hợp TRIGGERED / SUSPENDED / chưa cấu hình
 * được phân biệt bằng reason (namespace checkin của FE), không bằng ErrorCode.
 */
@Getter
public class DmsCheckinLinkException extends DmsException {

    public enum Reason {
        // Token sai / hết hạn / đã dùng / thuộc chu kỳ cũ: Owner vẫn đăng nhập để check-in thủ công được
        TOKEN_INVALID(ErrorCode.CHECKIN_TOKEN_INVALID),
        // Bàn giao đã bắt đầu: đăng nhập cũng không check-in được
        TRIGGERED(ErrorCode.CONFLICT),
        // Admin đang tạm dừng (Q28)
        SUSPENDED(ErrorCode.CONFLICT),
        // Chưa cấu hình, đã xoá cấu hình hoặc cấu hình thiếu lịch
        NOT_CONFIGURED(ErrorCode.CONFLICT);

        private final ErrorCode errorCode;

        Reason(ErrorCode errorCode) {
            this.errorCode = errorCode;
        }
    }

    private final Reason reason;

    public DmsCheckinLinkException(Reason reason, String message) {
        super(reason.errorCode, message);
        this.reason = reason;
    }
}
