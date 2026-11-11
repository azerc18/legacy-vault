package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
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
                    model, primaryEx.getMessage(), isRetryable(primaryEx));
            if (isRetryable(primaryEx) && backupModel != null && !backupModel.isBlank() && !backupModel.equalsIgnoreCase(model)) {
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

    /**
     * Chỉ chuyển sang model dự phòng khi lỗi có thể giải quyết được bằng việc đổi model / gọi lại:
     * 429 (hết quota / rate limit), 5xx (server error phía Google), hoặc lỗi mạng / timeout.
     * Không retry với 400 (Bad Request - sai API key, prompt vi phạm policy...) hoặc 401/403.
     */
    static boolean isRetryable(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof org.springframework.web.client.HttpStatusCodeException hsce) {
                int status = hsce.getStatusCode().value();
                return status == 429 || hsce.getStatusCode().is5xxServerError();
            }
            if (current instanceof org.springframework.web.client.ResourceAccessException) {
                return true;
            }
            if (current instanceof java.util.concurrent.TimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
