package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.HandoverStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class VaultHandoverProgressResponse {
    private final UUID vaultId;
    private final String vaultName;
    private final HandoverStatus handoverStatus;
    private final UUID requestId;
    private final LocalDateTime requestedAt;
    private final LocalDateTime decidedAt;
    private final String rejectionReason;
    private final LocalDateTime unlockedAt;
    private final LocalDateTime claimDeadlineAt;
}
