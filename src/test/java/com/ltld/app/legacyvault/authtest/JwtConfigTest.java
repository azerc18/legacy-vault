package com.ltld.app.legacyvault.authtest;

import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {
    @Test
    void shortSecret_failsFast() {
        JwtProperties props = new JwtProperties();
        props.setSecret("too-short");
        assertThatThrownBy(() -> new JwtConfig(props).jwtEncoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }
}
