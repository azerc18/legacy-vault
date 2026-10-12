package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service cho FR-11: Trao đổi tài liệu xác minh với Legal Verifier qua kênh tích hợp.
 */
public interface VerificationExchangeService {

    /**
     * Executor gửi tin nhắn hoặc tài liệu đính kèm bổ sung vào yêu cầu xác minh.
     */
    VerificationMessageResponse sendExecutorMessage(
            UUID verificationId, SendVerificationMessageRequest request, String executorLogin);

    /**
     * Lấy toàn bộ lịch sử trao đổi của một yêu cầu xác minh.
     */
    List<VerificationMessageResponse> getExchangeMessages(
            UUID verificationId, String executorLogin);
}
