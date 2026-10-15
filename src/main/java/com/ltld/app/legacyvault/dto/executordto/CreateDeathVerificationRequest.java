package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateDeathVerificationRequest {

    @NotEmpty(message = "At least one vault ID is required")
    @Size(max = 50, message = "Cannot request more than 50 vaults at once")
    private List<UUID> vaultIds;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    public CreateDeathVerificationRequest(List<UUID> vaultIds, String notes) {
        this.vaultIds = vaultIds;
        this.notes = notes;
    }
}
