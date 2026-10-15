package com.ltld.app.legacyvault.dto.executordto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ExecutorVaultBeneficiaryResponse {
    private final UUID id;
    private final String fullName;
}
