package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DeathVerificationResponse {
    private final UUID id;
    private final UUID assignmentId;
    private final LegalVerificationStatus status;
    private final List<UUID> vaultIds;
    private final String notes;
    private final LocalDateTime createdAt;
}
