package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorInvitationResponse;

import java.util.UUID;

/**
 * Lời mời làm Executor: người được Owner chỉ định tự đồng ý hoặc từ chối.
 * login = Principal.getName() (user id hoặc email). Lời mời không thuộc người gọi -> 404 như không tồn tại.
 */
public interface ExecutorInvitationService {

    ExecutorInvitationResponse getInvitation(UUID assignmentId, String login);

    /** PENDING -> ACCEPTED; đã trả lời / bị thu hồi / DMS đã TRIGGERED -> 409 EXECUTOR_INVITATION_CLOSED. */
    ExecutorInvitationResponse accept(UUID assignmentId, String login);

    /** PENDING -> DECLINED + REVOKED, gỡ role EXECUTOR nếu không còn giữ vị trí nào; cùng các lỗi như accept. */
    ExecutorInvitationResponse decline(UUID assignmentId, String login, String reason);
}
