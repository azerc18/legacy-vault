package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import com.ltld.app.legacyvault.dto.assistantdto.ChatRequest;
import com.ltld.app.legacyvault.exception.AssistantException;
import com.ltld.app.legacyvault.service.aifallbackservice.FallbackAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Vaulty: Gemini (cả model chính lẫn dự phòng) hỏng thì trả lời bằng Groq / DeepSeek. */
class AssistantServiceFallbackTest {

    private final UUID userId = UUID.randomUUID();
    private final ChatRequest request = new ChatRequest(List.of(new ChatMessage("user", "Làm sao tạo két?")));
    private AiClient gemini;
    private FallbackAiClient fallback;
    private AssistantServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        gemini = mock(AiClient.class);
        fallback = mock(FallbackAiClient.class);
        service = new AssistantServiceImpl(gemini, new ByteArrayResource("GUIDE".getBytes()));
        service.setFallback(fallback);
        when(gemini.chat(any(), any(), any())).thenThrow(new HttpServerErrorException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void geminiDown_fallbackAnswers() {
        when(fallback.textAvailable(any())).thenReturn(true);
        when(fallback.chat(any(), any(), any(), anyDouble(), anyInt())).thenReturn("  Vào Két của tôi → Tạo két mới  ");

        assertThat(service.chat(userId, List.of("OWNER"), request).reply()).isEqualTo("Vào Két của tôi → Tạo két mới");
    }

    @Test
    void geminiDown_fallbackAlsoDown_is503() {
        when(fallback.textAvailable(any())).thenReturn(true);
        when(fallback.chat(any(), any(), any(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service.chat(userId, List.of("OWNER"), request))
                .isInstanceOf(AssistantException.class);
    }

    @Test
    void geminiDown_noUsableFallback_is503_withoutCallingIt() {
        when(fallback.textAvailable(any())).thenReturn(false);

        assertThatThrownBy(() -> service.chat(userId, List.of("OWNER"), request))
                .isInstanceOf(AssistantException.class);
        verify(fallback, never()).chat(any(), any(), any(), anyDouble(), anyInt());
    }
}
