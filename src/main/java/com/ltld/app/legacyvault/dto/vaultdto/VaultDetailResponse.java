package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private VaultStatus status;
    private UUID ownerId;
    private String ownerEmail;
    private UUID beneficiaryId;
    private String beneficiaryEmail;
    private LocalDateTime unlockedAt;
    private LocalDateTime claimDeadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int totalDocuments;
    private int totalAssets;
}
