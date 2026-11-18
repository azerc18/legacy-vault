package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.service.integrationservice.IntegrationSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.BooleanSupplier;


/**
 * Mock dịch vụ eKYC (SRS BR-07): không gọi nhà cung cấp thật.
 * Quy tắc mock: số CCCD hợp lệ khi gồm đúng 12 chữ số và 3 số đầu là mã tỉnh nơi đăng ký khai sinh
 * (dùng chung {@link CitizenId#parse} với KYC của Owner), ví dụ 001 Hà Nội, 079 TP.HCM.
 * Khi có dịch vụ eKYC thật, chỉ cần thay nội dung class này.
 *
 * <p>Bật / tắt: Admin chọn ở trang Tích hợp (bảng integration_settings); chưa chọn thì theo APP_KYC_MOCK_ENABLED
 * (app.kyc.mock.enabled) như trước. Xem {@link IntegrationSettingsService#isKycMockEnabled()}.
 */
@Component
public class MockKycVerifier {

    private final BooleanSupplier enabled;

    @Autowired
    public MockKycVerifier(IntegrationSettingsService integrationSettings) {
        this(integrationSettings::isKycMockEnabled);
    }

    // Cho unit test: cờ cố định, không cần DB
    MockKycVerifier(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    // Giữ kiểm "enabled" làm lớp phòng thủ thứ hai (fail-closed), dù service đã chặn từ trước
    public boolean verify(String idNumber) {
        if (!isEnabled() || idNumber == null) {
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