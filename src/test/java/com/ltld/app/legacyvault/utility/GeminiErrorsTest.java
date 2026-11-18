package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiErrorsTest {

    @Test
    void isRetryable_trueForErrorsThatAnotherModelOrRetryCanFix() {
        assertThat(GeminiErrors.isRetryable(new HttpClientErrorException(HttpStatus.NOT_FOUND))).isTrue();
        assertThat(GeminiErrors.isRetryable(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
        assertThat(GeminiErrors.isRetryable(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))).isTrue();
        assertThat(GeminiErrors.isRetryable(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))).isTrue();
        assertThat(GeminiErrors.isRetryable(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))).isTrue();
        assertThat(GeminiErrors.isRetryable(new ResourceAccessException("I/O error"))).isTrue();
        // timeout nằm sâu trong chuỗi cause
        assertThat(GeminiErrors.isRetryable(
                new IllegalStateException("wrapped", new SocketTimeoutException("Read timed out")))).isTrue();
    }

    @Test
    void isRetryable_falseForErrorsTheBackupModelWouldHitToo() {
        assertThat(GeminiErrors.isRetryable(new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(GeminiErrors.isRetryable(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))).isFalse();
        assertThat(GeminiErrors.isRetryable(new HttpClientErrorException(HttpStatus.FORBIDDEN))).isFalse();
        assertThat(GeminiErrors.isRetryable(new IllegalArgumentException("bad json"))).isFalse();
        assertThat(GeminiErrors.isRetryable(null)).isFalse();
    }

    @Test
    void describe_showsCauseChain_statusAndNetworkMessage() {
        Exception timeout = new org.springframework.web.client.RestClientException("Error while extracting response",
                new SocketTimeoutException("Read timed out"));
        assertThat(GeminiErrors.describe(timeout))
                .isEqualTo("RestClientException <- SocketTimeoutException(Read timed out)");
        assertThat(GeminiErrors.describe(new HttpClientErrorException(HttpStatus.NOT_FOUND)))
                .isEqualTo("HttpClientErrorException(404)");
    }

    @Test
    void describe_neverIncludesParseErrorMessages_thatMayEchoDocumentContent() {
        Exception parse = new IllegalStateException("Unexpected token near \"citizenId\":\"001099012345\"");
        assertThat(GeminiErrors.describe(parse)).isEqualTo("IllegalStateException").doesNotContain("001099012345");
    }
}
