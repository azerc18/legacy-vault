package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorVerificationRequestResponse;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/** FR-10: tiến độ bàn giao của các Vault nằm trong yêu cầu xác minh do chính Executor này đã gửi. */
public interface ExecutorHandoverService {

    List<VaultHandoverProgressResponse> getHandoverProgress(UUID assignmentId, String executorLogin);

    /** FR-11: mọi hồ sơ xác minh Executor đã gửi cho Owner này, mới nhất trước, có số tin chưa đọc. */
    Page<ExecutorVerificationRequestResponse> listVerificationRequests(UUID assignmentId, String executorLogin,
                                                                       int page, int size);
}
