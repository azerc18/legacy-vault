package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;

import java.util.UUID;

public interface ExecutorAssetService {

    /**
     * FR-08: Lấy danh sách Vault thuộc Owner mà Executor được phân quyền quản lý kèm hướng dẫn bàn giao.
     *
     * @param assignmentId ID phân công của Executor (ExecutorAssignment)
     * @param executorLogin Định danh đăng nhập của caller (email hoặc userId)
     * @return DTO chứa thông tin Owner và danh sách Vaults
     */
    ExecutorAssignedVaultsResponse getAssignedVaults(UUID assignmentId, String executorLogin);
}
