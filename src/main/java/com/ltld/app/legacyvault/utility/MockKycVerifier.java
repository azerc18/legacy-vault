package com.ltld.app.legacyvault.utility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * Mock dịch vụ eKYC (SRS BR-07): không gọi nhà cung cấp thật.
 * Quy tắc mock: số CCCD hợp lệ khi gồm đúng 12 chữ số và 3 số đầu là mã tỉnh nơi đăng ký khai sinh
 * (dùng chung {@link CitizenId#parse} với KYC của Owner), ví dụ 001 Hà Nội, 079 TP.HCM.
 * Khi có dịch vụ eKYC thật, chỉ cần thay nội dung class này.
 */
@Component
public class MockKycVerifier {

    @Value("${app.kyc.mock.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    // Giữ kiểm "enabled" làm lớp phòng thủ thứ hai (fail-closed), dù service đã chặn từ trước
    public boolean verify(String idNumber) {
        if (!enabled || idNumber == null) {
            return false;
        }
        try {
            CitizenId.parse(idNumber);   // 12 chữ số + mã tỉnh hợp lệ
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}