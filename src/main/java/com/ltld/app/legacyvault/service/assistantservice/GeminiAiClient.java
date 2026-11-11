package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiAiClient implements AiClient {

    private final RestClient client;
    private final String apiKey;
    private final String backupApiKey;
    private final String model;
    private final String backupModel;

    public GeminiAiClient(@Value("${app.ai.gemini.api-key}") String apiKey,
                          @Value("${app.ai.gemini.backup-api-key:}") String backupApiKey,
                          @Value("${app.ai.gemini.model:gemini-3.6-flash}") String model,
                          @Value("${app.ai.gemini.backup-model:gemini-3.5-flash}") String backupModel) {
        this.apiKey = apiKey;
        this.backupApiKey = backupApiKey;
        this.model = model;
        this.backupModel = backupModel;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory)
                .build();
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> messages) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", messages.stream().map(m -> Map.of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content())))).toList(),
                "generationConfig", Map.of("temperature", 0.4, "maxOutputTokens", 2048));

        try {
            return executeChat(model, apiKey, body);
        } catch (Exception primaryEx) {
            log.warn("Primary Gemini model '{}' failed for Vaulty chat: {}. Attempting fallback to backup model...",
                    model, primaryEx.getMessage());
            if (backupModel != null && !backupModel.isBlank() && !backupModel.equalsIgnoreCase(model)) {
                try {
                    String keyToUse = (backupApiKey != null && !backupApiKey.isBlank()) ? backupApiKey : apiKey;
                    String reply = executeChat(backupModel, keyToUse, body);
                    log.info("Backup Gemini model '{}' succeeded for Vaulty chat", backupModel);
                    return reply;
                } catch (Exception backupEx) {
                    log.warn("Backup Gemini model '{}' also failed for Vaulty chat: {}",
                            backupModel, backupEx.getMessage());
                }
            }
            throw primaryEx instanceof RuntimeException re ? re : new RuntimeException(primaryEx);
        }
    }

    private String executeChat(String targetModel, String targetApiKey, Map<String, Object> body) {
        GeminiResponse res = client.post()
                .uri("/models/{model}:generateContent", targetModel)
                .header("x-goog-api-key", targetApiKey)
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
