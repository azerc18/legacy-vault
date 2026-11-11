package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lời mời làm Executor mà Owner gửi cho người đang đăng nhập (trang đồng ý / từ chối). */
@Getter
@Builder
public class ExecutorInvitationResponse {
    private UUID assignmentId;
    private String ownerFullName;
    // FE hiển thị thay cho ownerFullName khi Owner chưa có tên
    private String ownerEmail;
    private ExecutorType executorType;
    private ExecutorStatus status;
    private ExecutorResponseStatus responseStatus;
    private LocalDateTime assignedAt;
    private LocalDateTime respondedAt;
    // Số ngày Primary phải xác nhận sau khi DMS kích hoạt trước khi quyền chuyển sang Backup (BR-03), mặc định 14
    private int backupActivationWaitDays;
    // true khi còn trả lời được: đang chờ phản hồi, chưa bị thu hồi và DMS của Owner chưa bắt đầu bàn giao
    private boolean canRespond;
}
