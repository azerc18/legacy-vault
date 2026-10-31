package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VaultResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String description;
    private VaultStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String beneficiaryEmail;
    private boolean pendingRegistration;
    private long totalAssets;
    private long totalDocuments;
    private boolean modifiable;
    private String lockReason;
    @Builder.Default
    private List<VaultBeneficiaryResponse> beneficiaries = List.of();
}
