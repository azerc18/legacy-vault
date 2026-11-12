package com.ltld.app.legacyvault.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void notTrusted_ignoresHeader_soClientCannotSpoofIp() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4"))).isEqualTo("10.0.0.5");
    }

    @Test
    void trusted_withoutHeader_fallsBackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        assertThat(resolver.resolve(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
    }

    @Test
    void trusted_takesLastValue_notTheOneTheBrowserSent() {
        // Trình duyệt tự gửi "6.6.6.6", Azure nối IP thật vào cuối
        ClientIpResolver resolver = new ClientIpResolver(true);
        assertThat(resolver.resolve(request("10.0.0.5", "6.6.6.6, 203.0.113.7:51234"))).isEqualTo("203.0.113.7");
    }

    @Test
    void trusted_garbageHeader_fallsBackToRemoteAddr() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        assertThat(resolver.resolve(request("10.0.0.5", "not-an-ip"))).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "1.2.3.4, "))).isEqualTo("10.0.0.5");
    }

    @ParameterizedTest
    @CsvSource({
            "203.0.113.7, 203.0.113.7",
            "203.0.113.7:443, 203.0.113.7",
            "2001:db8::1, 2001:db8::1",
            "[2001:db8::1]:443, 2001:db8::1"
    })
    void stripsPort_soEachConnectionIsNotANewIp(String header, String expected) {
        assertThat(ClientIpResolver.lastForwardedFor(header)).isEqualTo(expected);
    }
}
