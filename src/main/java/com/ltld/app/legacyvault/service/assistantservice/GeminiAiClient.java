package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiAiClient implements AiClient {

    private final RestClient client;
    private final String model;

    public GeminiAiClient(@Value("${app.ai.gemini.api-key}") String apiKey,
                          @Value("${app.ai.gemini.model}") String model) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(factory)
                .build();
        this.model = model;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> messages) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", messages.stream().map(m -> Map.of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content())))).toList(),
                "generationConfig", Map.of("temperature", 0.4, "maxOutputTokens", 2048));

        GeminiResponse res = client.post()
                .uri("/models/{model}:generateContent", model)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);

        if (res == null || res.candidates() == null || res.candidates().isEmpty()) return "";
        var content = res.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) return "";
        return content.parts().get(0).text();
    }

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
