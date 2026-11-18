package com.ltld.app.legacyvault.service.aifallbackservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import com.ltld.app.legacyvault.enums.AiFallbackProvider;
import com.ltld.app.legacyvault.service.aisettingsservice.AiFeatureConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * AI dự phòng khi Gemini lỗi: Groq và DeepSeek, cùng API chuẩn OpenAI ({@code POST /chat/completions}).
 *
 * <ul>
 *   <li>Văn bản (chat Vaulty, chữ trích từ PDF): nhà cung cấp Admin chọn ở trang Cấu hình AI (mặc định
 *       {@code AI_FALLBACK_TEXT_PROVIDER}).</li>
 *   <li>Ảnh (JPG/PNG, trang PDF scan): luôn là Groq, vì DeepSeek không có model đọc ảnh.</li>
 * </ul>
 *
 * <p>API key chỉ đặt bằng biến môi trường (GROQ_API_KEY, DEEPSEEK_API_KEY); nhà cung cấp nào không có key thì
 * coi như tắt. Không log nội dung gửi / nhận vì có thể chứa thông tin giấy tờ tuỳ thân.
 */
@Slf4j
@Component
public class FallbackAiClient {

    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    private final RestClient client;

    private final String groqKey;
    private final String groqBaseUrl;
    private final String groqTextModel;
    private final String groqVisionModel;
    private final List<String> groqModels;

    private final String deepseekKey;
    private final String deepseekBaseUrl;
    private final String deepseekModel;
    private final List<String> deepseekModels;

    private final AiFallbackProvider defaultTextProvider;

    @Autowired
    public FallbackAiClient(@Value("${app.ai.groq.api-key:}") String groqKey,
                            @Value("${app.ai.groq.base-url:https://api.groq.com/openai/v1}") String groqBaseUrl,
                            @Value("${app.ai.groq.text-model:llama-3.3-70b-versatile}") String groqTextModel,
                            @Value("${app.ai.groq.vision-model:meta-llama/llama-4-scout-17b-16e-instruct}") String groqVisionModel,
                            @Value("${app.ai.groq.allowed-models:}") String groqExtra,
                            @Value("${app.ai.deepseek.api-key:}") String deepseekKey,
                            @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String deepseekBaseUrl,
                            @Value("${app.ai.deepseek.model:deepseek-chat}") String deepseekModel,
                            @Value("${app.ai.deepseek.allowed-models:}") String deepseekExtra,
                            @Value("${app.ai.fallback.text-provider:deepseek}") String defaultTextProvider) {
        this(groqKey, groqBaseUrl, groqTextModel, groqVisionModel, groqExtra,
                deepseekKey, deepseekBaseUrl, deepseekModel, deepseekExtra, defaultTextProvider,
                RestClient.builder().requestFactory(timeouts()));
    }

    // Cho unit test truyền RestClient.Builder gắn MockRestServiceServer
    FallbackAiClient(String groqKey, String groqBaseUrl, String groqTextModel, String groqVisionModel, String groqExtra,
                     String deepseekKey, String deepseekBaseUrl, String deepseekModel, String deepseekExtra,
                     String defaultTextProvider, RestClient.Builder builder) {
        this.client = builder.build();
        this.groqKey = blankToNull(groqKey);
        this.groqBaseUrl = trimSlash(groqBaseUrl);
        this.groqTextModel = blankToNull(groqTextModel);
        this.groqVisionModel = blankToNull(groqVisionModel);
        this.groqModels = models(groqExtra, this.groqTextModel, this.groqVisionModel);
        this.deepseekKey = blankToNull(deepseekKey);
        this.deepseekBaseUrl = trimSlash(deepseekBaseUrl);
        this.deepseekModel = blankToNull(deepseekModel);
        this.deepseekModels = models(deepseekExtra, this.deepseekModel);
        AiFallbackProvider p = AiFallbackProvider.parse(defaultTextProvider);
        this.defaultTextProvider = p != null ? p : AiFallbackProvider.DEEPSEEK;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        return factory;
    }

    // ------------------------------------------------------------------ cấu hình (trang Cấu hình AI đọc)

    public boolean isConfigured(AiFallbackProvider provider) {
        return provider == AiFallbackProvider.GROQ ? groqKey != null : deepseekKey != null;
    }

    public List<String> allowedModels(AiFallbackProvider provider) {
        return provider == AiFallbackProvider.GROQ ? groqModels : deepseekModels;
    }

    public AiFallbackProvider defaultTextProvider() {
        return defaultTextProvider;
    }

    public String defaultTextModel(AiFallbackProvider provider) {
        return provider == AiFallbackProvider.GROQ ? groqTextModel : deepseekModel;
    }

    public String defaultVisionModel() {
        return groqVisionModel;
    }

    // ------------------------------------------------------------------ chọn theo cấu hình của chức năng

    /** Nhà cung cấp văn bản của chức năng (Admin chọn, nếu không thì mặc định). */
    public AiFallbackProvider textProvider(AiFeatureConfig config) {
        AiFallbackProvider p = config == null ? null : config.textFallbackProvider();
        return p != null ? p : defaultTextProvider;
    }

    /** Dự phòng văn bản dùng được: chức năng không tắt dự phòng và nhà cung cấp đã chọn có key. */
    public boolean textAvailable(AiFeatureConfig config) {
        return enabled(config) && isConfigured(textProvider(config));
    }

    /** Dự phòng đọc ảnh dùng được: chức năng không tắt dự phòng, Groq có key và có model vision. */
    public boolean visionAvailable(AiFeatureConfig config) {
        return enabled(config) && groqKey != null && visionModel(config) != null;
    }

    private static boolean enabled(AiFeatureConfig config) {
        return config == null || config.fallbackEnabled();
    }

    private String textModel(AiFeatureConfig config, AiFallbackProvider provider) {
        String chosen = config == null ? null : config.textFallbackModel();
        return chosen != null && allowedModels(provider).contains(chosen) ? chosen : defaultTextModel(provider);
    }

    private String visionModel(AiFeatureConfig config) {
        String chosen = config == null ? null : config.visionFallbackModel();
        return chosen != null && groqModels.contains(chosen) ? chosen : groqVisionModel;
    }

    /** Tên hiển thị của bước dự phòng văn bản, vd. "DEEPSEEK deepseek-chat" (cho log / audit, không có nội dung). */
    public String describeText(AiFeatureConfig config) {
        AiFallbackProvider p = textProvider(config);
        return p + " " + textModel(config, p);
    }

    // ------------------------------------------------------------------ gọi API

    /** Chat văn bản (Vaulty). Lỗi thì ném RuntimeException như Gemini để nơi gọi xử lý giống nhau. */
    public String chat(AiFeatureConfig config, String systemPrompt, List<ChatMessage> messages,
                       double temperature, int maxTokens) {
        AiFallbackProvider p = textProvider(config);
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessage m : messages) {
            msgs.add(Map.of("role", "assistant".equals(m.role()) ? "assistant" : "user", "content", m.content()));
        }
        return complete(p, textModel(config, p), msgs, temperature, maxTokens, false);
    }

    /** Một lượt hỏi văn bản, trả JSON (vd. chữ trích từ PDF giấy chứng tử). */
    public String textJson(AiFeatureConfig config, String systemPrompt, String userText, int maxTokens) {
        AiFallbackProvider p = textProvider(config);
        List<Map<String, Object>> msgs = List.of(
                Map.of("role", "system", "content", systemPrompt + "\nRespond with one json object only."),
                Map.of("role", "user", "content", userText));
        return complete(p, textModel(config, p), msgs, 0.1, maxTokens, true);
    }

    /** Đọc ảnh bằng Groq vision, trả JSON. Ảnh đã được thu nhỏ thành JPEG ({@link AiImages}). */
    public String visionJson(AiFeatureConfig config, String prompt, List<byte[]> jpegImages, int maxTokens) {
        List<Object> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", prompt + "\nRespond with one json object only."));
        for (byte[] img : jpegImages) {
            parts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(img))));
        }
        List<Map<String, Object>> msgs = List.of(Map.of("role", "user", "content", parts));
        return complete(AiFallbackProvider.GROQ, visionModel(config), msgs, 0.0, maxTokens, true);
    }

    public String visionModelName(AiFeatureConfig config) {
        return visionModel(config);
    }

    private String complete(AiFallbackProvider provider, String model, List<Map<String, Object>> messages,
                            double temperature, int maxTokens, boolean json) {
        String key = provider == AiFallbackProvider.GROQ ? groqKey : deepseekKey;
        String base = provider == AiFallbackProvider.GROQ ? groqBaseUrl : deepseekBaseUrl;
        if (key == null || model == null) {
            throw new IllegalStateException(provider + " fallback is not configured");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        JsonNode res;
        try {
            byte[] rawBytes = client.post()
                    .uri(base + "/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            // JSON luôn là UTF-8; không để Spring đoán charset theo Content-Type
            String raw = rawBytes == null ? null : new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
            res = raw == null || raw.isBlank() ? null : JSON.readTree(raw);
        } catch (HttpStatusCodeException e) {
            // Ghi rõ lỗi của nhà cung cấp (vd. 404 "model ... does not exist") để Admin biết phải đổi model / key
            throw new AiProviderException(provider + " model '" + model + "' HTTP " + e.getStatusCode().value()
                    + ": " + providerError(e.getResponseBodyAsString()), e.getStatusCode().value(), e);
        }
        String text = res == null ? null
                : res.path("choices").path(0).path("message").path("content").asString(null);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Empty response from " + provider);
        }
        log.info("AI fallback {} model '{}' answered", provider, model);
        return text;
    }

    /** Thông báo lỗi trong body lỗi chuẩn OpenAI ({"error":{"message":...}}), cắt ngắn; không đọc được thì để trống. */
    static String providerError(String body) {
        if (body == null || body.isBlank()) return "(no message)";
        try {
            String m = JSON.readTree(body).path("error").path("message").asString(null);
            if (m == null || m.isBlank()) return "(no message)";
            return m.length() > 200 ? m.substring(0, 200) + "..." : m;
        } catch (RuntimeException e) {
            return "(unreadable error body)";
        }
    }

    /** Lấy khối JSON {...} trong câu trả lời (model có thể bọc ```json hoặc thêm câu dẫn). */
    public static String jsonObject(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : raw.strip();
    }

    // ------------------------------------------------------------------ nội bộ

    private static List<String> models(String extra, String... defaults) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String d : defaults) {
            if (d != null) set.add(d);
        }
        if (extra != null) {
            for (String m : extra.split(",")) {
                String t = m.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        return List.copyOf(set);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trimSlash(String url) {
        String u = url == null ? "" : url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
