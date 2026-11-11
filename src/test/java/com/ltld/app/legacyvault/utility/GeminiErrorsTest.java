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
}
