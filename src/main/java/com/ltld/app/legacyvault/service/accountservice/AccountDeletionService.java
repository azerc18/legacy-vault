package com.ltld.app.legacyvault.service.accountservice;

import com.ltld.app.legacyvault.dto.accountdto.AccountDeletionCheckResponse;
import com.ltld.app.legacyvault.dto.accountdto.DeleteAccountRequest;

import java.util.UUID;

// Người dùng tự xoá tài khoản (trang Hồ sơ). Xoá mềm: ẩn danh dữ liệu cá nhân, giữ dòng users cho khoá ngoại.
public interface AccountDeletionService {
    AccountDeletionCheckResponse check(UUID userId);

    void delete(UUID userId, DeleteAccountRequest request);
}
