package com.ltld.app.legacyvault.dto.executordto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ExecutorAssignedVaultsResponse {
    private final UUID assignmentId;
    private final String ownerFullName;
    private final List<ExecutorVaultResponse> vaults;
}
