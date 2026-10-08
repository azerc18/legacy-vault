package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VaultResponse {
    private UUID id;
    private String name;
    private String description;
    private VaultStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
