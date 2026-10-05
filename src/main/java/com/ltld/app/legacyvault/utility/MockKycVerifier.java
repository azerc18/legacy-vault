package com.ltld.app.legacyvault.utility;

import org.springframework.stereotype.Component;

/**
 * Mock dịch vụ eKYC (SRS BR-07): không gọi nhà cung cấp thật.
 * Quy tắc mock: số CCCD hợp lệ khi gồm đúng 12 chữ số.
 * Khi có dịch vụ eKYC thật, chỉ cần thay nội dung class này.
 */
@Component
public class MockKycVerifier {

    public boolean verify(String idNumber) {
        return idNumber != null && idNumber.matches("\\d{12}");
    }
}