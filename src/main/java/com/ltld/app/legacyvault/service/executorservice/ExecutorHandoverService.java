package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;

import java.util.List;
import java.util.UUID;

/** FR-10: tiến độ bàn giao của các Vault nằm trong yêu cầu xác minh do chính Executor này đã gửi. */
public interface ExecutorHandoverService {

    List<VaultHandoverProgressResponse> getHandoverProgress(UUID assignmentId, String executorLogin);
}
