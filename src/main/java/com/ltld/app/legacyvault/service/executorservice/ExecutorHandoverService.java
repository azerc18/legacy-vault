package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service cho FR-10: Theo dõi tiến độ bàn giao từng tài sản / Vault.
 */
public interface ExecutorHandoverService {

    /**
     * Lấy tiến độ bàn giao các Vault thuộc Owner mà Executor được phân công quản lý.
     *
     * @param assignmentId  id của ExecutorAssignment
     * @param executorLogin email hoặc id của Executor đang đăng nhập
     * @return danh sách tiến độ bàn giao từng Vault
     */
    List<VaultHandoverProgressResponse> getHandoverProgress(UUID assignmentId, String executorLogin);

    /**
     * Lấy tiến độ bàn giao các Vault dựa trên phiên đăng nhập hiện tại của Executor.
     *
     * @param executorLogin email hoặc id của Executor đang đăng nhập
     * @return danh sách tiến độ bàn giao từng Vault
     */
    List<VaultHandoverProgressResponse> getHandoverProgress(String executorLogin);
}
