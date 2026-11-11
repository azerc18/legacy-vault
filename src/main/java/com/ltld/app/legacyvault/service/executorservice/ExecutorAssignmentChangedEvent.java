package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.List;
import java.util.UUID;

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
        REVOKED_TIMEOUT,
        // FR-07: Admin chỉ định Executor mới thay người cũ
        REPLACED_BY_ADMIN
    }

    /**
     * Một mail cho một Executor; chỉ chứa thông tin của Owner và vị trí của chính người nhận.
     * assignmentId: chỉ có ở ASSIGNED (lời mời), để mail và thông báo trên chuông dẫn tới trang đồng ý / từ chối.
     */
    public record ExecutorMail(MailType type, String recipientEmail, String ownerName, String ownerEmail,
                               ExecutorType executorType, int waitDays, UUID assignmentId) {

        public static ExecutorMail assigned(String recipientEmail, String ownerName, String ownerEmail,
                                            ExecutorType executorType, UUID assignmentId) {
            return new ExecutorMail(MailType.ASSIGNED, recipientEmail, ownerName, ownerEmail, executorType, 0,
                    assignmentId);
        }

        public static ExecutorMail revoked(String recipientEmail, String ownerName, String ownerEmail,
                                           ExecutorType executorType) {
            return new ExecutorMail(MailType.REVOKED, recipientEmail, ownerName, ownerEmail, executorType, 0, null);
        }

        public static ExecutorMail revokedByTimeout(String recipientEmail, String ownerName, String ownerEmail,
                                                    int waitDays) {
            return new ExecutorMail(MailType.REVOKED_TIMEOUT, recipientEmail, ownerName, ownerEmail,
                    ExecutorType.PRIMARY, waitDays, null);
        }

        public static ExecutorMail replacedByAdmin(String recipientEmail, String ownerName, String ownerEmail,
                                                   ExecutorType executorType) {
            return new ExecutorMail(MailType.REPLACED_BY_ADMIN, recipientEmail, ownerName, ownerEmail,
                    executorType, 0, null);
        }
    }
}
