package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;

import java.util.UUID;

public interface ExecutorAssetService {

    ExecutorAssignedVaultsResponse getAssignedVaults(UUID assignmentId, String executorLogin);
}
