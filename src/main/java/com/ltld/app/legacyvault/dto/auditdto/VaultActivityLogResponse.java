package com.ltld.app.legacyvault.dto.auditdto;

import com.ltld.app.legacyvault.enums.ActionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VaultActivityLogResponse {
    private UUID id;
    private ActionType actionType;
    private String description;
    private String ipAddress;
    private LocalDateTime createdAt;
}
