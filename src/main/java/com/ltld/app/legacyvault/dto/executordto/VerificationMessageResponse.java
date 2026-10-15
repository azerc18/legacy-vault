package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class VerificationMessageResponse {
    private final UUID id;
    private final UUID requestId;
    private final ExchangeSenderRole senderRole;
    private final String senderName;
    private final String content;
    private final String attachmentName;
    private final Long attachmentSize;
    private final LocalDateTime createdAt;
}
