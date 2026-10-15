package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;

import java.util.UUID;

/** FR-08: Vault/tài sản của Owner mà Executor ACTIVE được ủy quyền; chỉ metadata, không giải mã. */
public interface ExecutorAssetService {

    ExecutorAssignedVaultsResponse getAssignedVaults(UUID assignmentId, String executorLogin);
}
