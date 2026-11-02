package com.ltld.app.legacyvault.service.mfaservice;

import com.ltld.app.legacyvault.dto.mfadto.MfaSetupResponse;
import com.ltld.app.legacyvault.entity.User;

import java.util.List;
import java.util.UUID;

public interface MfaService {

    // Sinh secret mới (chưa bật 2FA), trả QR để quét
    MfaSetupResponse setup(UUID userId);

    // Xác nhận mã đầu tiên -> bật 2FA, trả 10 mã dự phòng (bản gốc, chỉ 1 lần)
    List<String> enable(UUID userId, String code);

    // Tắt 2FA, bắt buộc nhập mã TOTP hoặc mã dự phòng
    void disable(UUID userId, String code);

    // Dùng chung cho disable và đăng nhập 2 bước (H4-2). Gọi trong transaction.
    boolean verifyCode(User user, String code);
}