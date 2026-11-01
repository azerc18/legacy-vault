package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class VaultDetailResponse {
    private final UUID id;
    private final String name;
    private final String description;
    private final VaultStatus status;
    private final UUID ownerId;
    private final String ownerEmail;
    private final UUID beneficiaryId;
    private final String beneficiaryEmail;
    private final LocalDateTime unlockedAt;
    private final LocalDateTime claimDeadlineAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final long totalDocuments;
    private final long totalAssets;
    private final boolean modifiable;
    private final String lockReason;
    @Builder.Default
    private final List<VaultBeneficiaryResponse> beneficiaries = List.of();
}
