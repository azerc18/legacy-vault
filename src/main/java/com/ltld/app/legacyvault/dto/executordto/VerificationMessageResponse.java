package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO hiển thị tin nhắn/tài liệu trong luồng trao đổi của yêu cầu xác minh (FR-11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationMessageResponse {

    private UUID id;
    private UUID verificationId;
    private UUID senderId;
    private String senderName;
    private String senderEmail;
    private ExchangeSenderRole senderRole;
    private String messageContent;
    private String attachmentUrl;
    private String attachmentName;
    private Long attachmentSize;
    private LocalDateTime createdAt;
}
