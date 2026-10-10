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
        assertThat(verifier(false).verify("123456789012")).isFalse();
    }

    @Test
    void enabled_twelveDigits_true() {
        assertThat(verifier(true).isEnabled()).isTrue();
        assertThat(verifier(true).verify("123456789012")).isTrue();
    }

    @Test
    void enabled_elevenDigitsOrLetters_false() {
        assertThat(verifier(true).verify("12345678901")).isFalse();
        assertThat(verifier(true).verify("12345678901a")).isFalse();
        assertThat(verifier(true).verify(null)).isFalse();
    }
}