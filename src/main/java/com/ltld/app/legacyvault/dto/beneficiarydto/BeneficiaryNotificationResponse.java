package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class BeneficiaryNotificationResponse {
    private UUID id;
    private NotificationType type;
    private String content;
    private UUID vaultId;
    private boolean read;
    private LocalDateTime createdAt;
}
