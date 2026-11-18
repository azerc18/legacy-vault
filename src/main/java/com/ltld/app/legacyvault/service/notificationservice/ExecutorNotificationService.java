package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.enums.ExecutorType;

import java.time.LocalDateTime;

public interface ExecutorNotificationService {

    /** Mail bàn giao dạng câu tiếng Anh dựng sẵn (bọc trong bố cục HTML chung). Lỗi gửi ném RuntimeException. */
    void send(String recipientEmail, String content);

    /** Mail cảnh báo cho Admin (câu tiếng Anh dựng sẵn, tiêu đề "Admin alert"). Lỗi gửi ném RuntimeException. */
    void sendAdminAlert(String adminEmail, String content);

    /**
     * FR-07: mail "DMS đã kích hoạt" cho Executor, dịch theo ngôn ngữ của người nhận, có nút mở trang Người thực thi.
     * Lỗi gửi ném RuntimeException để người gọi ghi Notification FAILED.
     */
    void sendExecutorActivated(String recipientEmail, ExecutorActivatedMail mail);

    /**
     * Dữ liệu của mail kích hoạt. waitDays / acknowledgeBy chỉ có với Primary (BR-03), Backup để null.
     */
    record ExecutorActivatedMail(String executorName, String ownerName, String ownerEmail, ExecutorType type,
                                 Integer waitDays, LocalDateTime acknowledgeBy) {
    }
}
