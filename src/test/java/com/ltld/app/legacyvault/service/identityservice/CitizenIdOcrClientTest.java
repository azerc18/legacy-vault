package com.ltld.app.legacyvault.service.identityservice;

import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.service.aifallbackservice.AiImagesTestSupport;
import com.ltld.app.legacyvault.service.aifallbackservice.FallbackAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI dự phòng của đọc ảnh CCCD: Gemini không dùng được thì đọc bằng Groq vision. */
class CitizenIdOcrClientTest {

    private CitizenIdOcrClient client;
    private FallbackAiClient fallback;

    @BeforeEach
    void setUp() {
        client = new CitizenIdOcrClient(JsonMapper.builder().build());
        // Gemini chưa cấu hình (AI_PROVIDER=mock, không key)
        ReflectionTestUtils.setField(client, "aiProvider", "mock");
        ReflectionTestUtils.setField(client, "apiKey", "");
        ReflectionTestUtils.setField(client, "model", "gemini-3.6-flash");
        ReflectionTestUtils.setField(client, "backupModel", "gemini-3.5-flash");
        fallback = mock(FallbackAiClient.class);
    }

    @Test
    void noGemini_noFallback_isUnavailable() {
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.read(new byte[]{1}))
                .isInstanceOf(UserProfileException.class);
    }

    @Test
    void noGemini_withGroqVision_isAvailable_andReadsWithGroq() throws Exception {
        ReflectionTestUtils.setField(client, "fallback", fallback);
        when(fallback.visionAvailable(any())).thenReturn(true);
        when(fallback.visionJson(any(), any(), any(), anyInt())).thenReturn("""
                Kết quả:
                {"documentType":"CCCD","citizenId":"001099012345","fullName":"Trần Long Vân",
                 "dateOfBirth":"01/02/1990","gender":"Nam","placeOfResidence":null}
                """);

        assertThat(client.isAvailable()).isTrue();
        CitizenIdOcrClient.Extracted x = client.read(AiImagesTestSupport.png(1200, 800));

        assertThat(x.citizenId()).isEqualTo("001099012345");
        assertThat(x.fullName()).isEqualTo("Trần Long Vân");
        assertThat(x.placeOfResidence()).isNull();
        verify(fallback).visionJson(any(), eq(CitizenIdOcrClient.PROMPT), argThat(list -> list.size() == 1), eq(512));
    }

    @Test
    void groqFails_isReportedAsUnavailable_notAsServerError() throws Exception {
        ReflectionTestUtils.setField(client, "fallback", fallback);
        when(fallback.visionAvailable(any())).thenReturn(true);
        when(fallback.visionJson(any(), any(), any(), anyInt())).thenThrow(new IllegalStateException("Groq down"));

        assertThatThrownBy(() -> client.read(AiImagesTestSupport.png(100, 100)))
                .isInstanceOf(UserProfileException.class);
    }
}
