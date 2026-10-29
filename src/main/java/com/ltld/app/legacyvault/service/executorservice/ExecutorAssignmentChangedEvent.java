package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.List;

/**
 * FR-06 bước 5: phát ra khi Owner chỉ định / thay thế / thu hồi Executor, mang sẵn các mail cần gửi.
 * Listener gửi mail ở AFTER_COMMIT, nên transaction rollback (409, lỗi khóa...) thì không mail nào được gửi.
 */
public record ExecutorAssignmentChangedEvent(List<ExecutorMail> mails) {

    public ExecutorAssignmentChangedEvent {
        mails = List.copyOf(mails);
    }

    public enum MailType {
        ASSIGNED, REVOKED,
        // FR-07 / BR-03: hệ thống tự thu hồi Primary vì không xác nhận trong hạn, không phải Owner thu hồi
        REVOKED_TIMEOUT
    }

    /** Một mail cho một Executor; chỉ chứa thông tin của Owner và vị trí của chính người nhận. */
    public record ExecutorMail(MailType type, String recipientEmail, String ownerName, String ownerEmail,
                               ExecutorType executorType, int waitDays) {

        public static ExecutorMail assigned(String recipientEmail, String ownerName, String ownerEmail,
                                            ExecutorType executorType) {
            return new ExecutorMail(MailType.ASSIGNED, recipientEmail, ownerName, ownerEmail, executorType, 0);
        }

        public static ExecutorMail revoked(String recipientEmail, String ownerName, String ownerEmail,
                                           ExecutorType executorType) {
            return new ExecutorMail(MailType.REVOKED, recipientEmail, ownerName, ownerEmail, executorType, 0);
        }

        public static ExecutorMail revokedByTimeout(String recipientEmail, String ownerName, String ownerEmail,
                                                    int waitDays) {
            return new ExecutorMail(MailType.REVOKED_TIMEOUT, recipientEmail, ownerName, ownerEmail,
                    ExecutorType.PRIMARY, waitDays);
        }
    }
}
