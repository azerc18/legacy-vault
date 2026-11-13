package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MockKycVerifierTest {

    private MockKycVerifier verifier(boolean enabled) {
        MockKycVerifier v = new MockKycVerifier();
        ReflectionTestUtils.setField(v, "enabled", enabled);
        return v;
    }

    @Test
    void disabled_isEnabledFalse_andVerifyFalseEvenForValidId() {
        assertThat(verifier(false).isEnabled()).isFalse();
        assertThat(verifier(false).verify("079203123456")).isFalse();
    }

    @Test
    void enabled_validProvinceCode_true() {
        assertThat(verifier(true).isEnabled()).isTrue();
        assertThat(verifier(true).verify("001203123456")).isTrue();   // Hà Nội
        assertThat(verifier(true).verify("079203123456")).isTrue();   // TP.HCM
        assertThat(verifier(true).verify("077200111222")).isTrue();   // Bà Rịa - Vũng Tàu
        assertThat(verifier(true).verify("082195654321")).isTrue();   // Tiền Giang
    }

    @Test
    void enabled_spacesAreIgnored_true() {
        assertThat(verifier(true).verify("079 203 123 456")).isTrue();
    }

    @Test
    void enabled_twelveDigitsButInvalidProvinceCode_false() {
        // Trước đây 12 chữ số bất kỳ đều qua; giờ 3 số đầu phải là mã tỉnh
        assertThat(verifier(true).verify("000000000000")).isFalse();
        assertThat(verifier(true).verify("123456789012")).isFalse();
        assertThat(verifier(true).verify("003203123456")).isFalse();   // 003 không phải mã tỉnh
        assertThat(verifier(true).verify("097203123456")).isFalse();   // ngoài dải 001-096
    }

    @Test
    void enabled_elevenDigitsOrLetters_false() {
        assertThat(verifier(true).verify("12345678901")).isFalse();
        assertThat(verifier(true).verify("12345678901a")).isFalse();
        assertThat(verifier(true).verify(null)).isFalse();
    }
}