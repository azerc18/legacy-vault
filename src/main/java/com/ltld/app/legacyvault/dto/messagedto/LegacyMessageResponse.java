package com.ltld.app.legacyvault.dto.messagedto;

import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegacyMessageResponse {

    private UUID id;
    private UUID vaultId;
    private LegacyMessageRecipientType recipientType;
    private UUID recipientBeneficiaryId;
    private String recipientName;
    private String recipientEmail;
    private String title;
    private String content;
    private LegacyMessageDeliveryTrigger deliveryTrigger;
    private LocalDate deliverDate;
    private LegacyMessageStatus status;
    private boolean hasAttachment;
    private String attachmentName;
    private String attachmentType;
    private Long attachmentSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deliveredAt;
    private boolean unlocked;
}

