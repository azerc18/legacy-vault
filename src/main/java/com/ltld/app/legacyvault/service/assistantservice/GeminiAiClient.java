package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import com.ltld.app.legacyvault.service.aisettingsservice.AiFeatureConfig;
import com.ltld.app.legacyvault.utility.GeminiErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder()
            .configure(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();

    private final RestClient client;
    private final String apiKey;
    private final String backupApiKey;
    private final String model;
    private final String backupModel;

    @Autowired
    public GeminiAiClient(@Value("${app.ai.gemini.api-key}") String apiKey,
                          @Value("${app.ai.gemini.backup-api-key:}") String backupApiKey,
                          @Value("${app.ai.gemini.model:gemini-3.6-flash}") String model,
                          @Value("${app.ai.gemini.backup-model:gemini-3.5-flash}") String backupModel) {
        this(apiKey, backupApiKey, model, backupModel, RestClient.builder().requestFactory(timeouts()));
    }

    GeminiAiClient(String apiKey,
                   String backupApiKey,
                   String model,
                   String backupModel,
                   RestClient.Builder builder) {
        this.apiKey = apiKey;
        this.backupApiKey = backupApiKey;
        this.model = model;
        this.backupModel = backupModel;
        this.client = builder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> messages) {
        return chat(systemPrompt, messages, null);
    }

    /** Model chính / dự phòng lấy theo trang Cấu hình AI; config null thì dùng model của biến môi trường. */
    @Override
    public String chat(String systemPrompt, List<ChatMessage> messages, AiFeatureConfig config) {
        String model = config != null && config.primaryModel() != null ? config.primaryModel() : this.model;
        String backupModel = config != null ? config.backupModel() : this.backupModel;
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", messages.stream().map(m -> Map.of(
                        "role", "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content())))).toList(),
                "generationConfig", Map.of("temperature", 0.4, "maxOutputTokens", 2048));

        try {
            return executeChat(model, apiKey, body);
        } catch (Exception primaryEx) {
            log.warn("Primary Gemini model '{}' failed for Vaulty chat: {}. Retryable: {}",
                    model, GeminiErrors.describe(primaryEx), GeminiErrors.isRetryable(primaryEx));
            if (GeminiErrors.isRetryable(primaryEx) && backupModel != null && !backupModel.isBlank() && !backupModel.equalsIgnoreCase(model)) {
                try {
                    String keyToUse = (backupApiKey != null && !backupApiKey.isBlank()) ? backupApiKey : apiKey;
                    String reply = executeChat(backupModel, keyToUse, body);
                    log.info("Backup Gemini model '{}' succeeded for Vaulty chat", backupModel);
                    return reply;
                } catch (Exception backupEx) {
                    log.warn("Backup Gemini model '{}' also failed for Vaulty chat: {}",
                            backupModel, GeminiErrors.describe(backupEx));
                }
            }
            throw primaryEx instanceof RuntimeException re ? re : new RuntimeException(primaryEx);
        }
    }

    private String executeChat(String targetModel, String targetApiKey, Map<String, Object> body) {
        byte[] rawBytes = client.post()
                .uri("/models/{model}:generateContent", targetModel)
                .header("x-goog-api-key", targetApiKey)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // Đọc byte rồi tự parse: Gemini có lúc trả Content-Type application/octet-stream cho body JSON
                .body(byte[].class);
        // JSON luôn là UTF-8; không để Spring đoán charset theo Content-Type (octet-stream -> ISO-8859-1 làm hỏng tiếng Việt)
        String rawBody = rawBytes == null ? null : new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
        GeminiResponse res = rawBody == null || rawBody.isBlank() ? null : JSON.readValue(rawBody, GeminiResponse.class);

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
