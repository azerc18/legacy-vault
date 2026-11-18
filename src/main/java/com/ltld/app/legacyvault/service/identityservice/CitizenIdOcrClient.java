package com.ltld.app.legacyvault.service.identityservice;

import com.ltld.app.legacyvault.service.aifallbackservice.AiImages;
import com.ltld.app.legacyvault.service.aifallbackservice.FallbackAiClient;
import com.ltld.app.legacyvault.enums.AiFeature;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.service.aisettingsservice.AiFeatureConfig;
import com.ltld.app.legacyvault.service.aisettingsservice.AiSettingsService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.utility.GeminiErrors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Đọc ảnh CCCD bằng Gemini Vision. Ảnh chỉ nằm trong bộ nhớ trong lúc gọi, không lưu.
 * AI tắt hoặc lỗi thì báo IDENTITY_OCR_UNAVAILABLE để FE gợi ý quét QR hoặc nhập tay; không bao giờ trả dữ liệu bịa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CitizenIdOcrClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 25000;

    /** Các trường AI đọc được; null khi không đọc được. Ngày dạng dd/MM/yyyy như in trên thẻ. */
    public record Extracted(String citizenId, String fullName, String dateOfBirth, String gender,
                            String placeOfResidence, String documentType) {
    }

    private final ObjectMapper objectMapper;

    // Dùng chung cho Gemini và Groq vision (AI dự phòng)
    static final String PROMPT = "Ảnh đính kèm là mặt trước Căn cước công dân / Căn cước Việt Nam. "
            + "CHỈ trích xuất thông tin thực sự đọc được; trường nào không đọc được thì trả null, "
            + "TUYỆT ĐỐI không đoán hay bịa. Trả về đúng JSON với các trường: "
            + "documentType (\"CCCD\" nếu đúng là thẻ căn cước Việt Nam, ngược lại \"OTHER\"), "
            + "citizenId (12 chữ số, không khoảng trắng), fullName (giữ nguyên dấu), "
            + "dateOfBirth (dd/MM/yyyy), gender (\"Nam\" hoặc \"Nữ\"), "
            + "placeOfResidence (nơi thường trú / nơi cư trú, nguyên văn).";

    @Value("${app.ai.provider:mock}")
    private String aiProvider;

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.model:gemini-3.6-flash}")
    private String model;

    @Value("${app.ai.gemini.backup-api-key:}")
    private String backupApiKey;

    @Value("${app.ai.gemini.backup-model:gemini-3.5-flash}")
    private String backupModel;

    // Trang Cấu hình AI (bật / tắt, model). Không có (unit test) thì luôn bật, model của biến môi trường.
    @Autowired(required = false)
    private AiSettingsService aiSettings;

    // AI dự phòng (Groq vision) khi Gemini hỏng hoặc chưa cấu hình. Không có (unit test) thì chỉ Gemini.
    @Autowired(required = false)
    private FallbackAiClient fallback;

    /** Đọc được ảnh: chức năng đang bật, và có Gemini hoặc Groq vision dùng được. */
    public boolean isAvailable() {
        AiFeatureConfig c = config();
        return c.enabled() && (geminiReady() || fallbackReady(c));
    }

    private boolean geminiReady() {
        return "gemini".equalsIgnoreCase(aiProvider) && apiKey != null && !apiKey.isBlank();
    }

    private boolean fallbackReady(AiFeatureConfig c) {
        return fallback != null && fallback.visionAvailable(c);
    }

    private AiFeatureConfig config() {
        AiFeatureConfig c = aiSettings == null ? null : aiSettings.forFeature(AiFeature.CITIZEN_ID_OCR);
        return c != null ? c : new AiFeatureConfig(true, model, backupModel);
    }

    /**
     * Đọc ảnh bằng model chính; model chính hết quota / quá tải / bị ngừng (lỗi có thể thử lại) thì thử model
     * dự phòng (và key dự phòng nếu có), giống OCR giấy chứng tử và Vaulty.
     */
    public Extracted read(byte[] imageBytes) {
        if (!isAvailable()) {
            throw unavailable();
        }
        AiFeatureConfig c = config();
        if (!geminiReady()) {
            return readWithFallback(imageBytes, c);
        }
        String primary = c.primaryModel() != null ? c.primaryModel() : model;
        try {
            return readWith(imageBytes, primary, apiKey);
        } catch (UserProfileException e) {
            throw e;
        } catch (Exception primaryEx) {
            String backup = c.backupModel();
            boolean canRetry = GeminiErrors.isRetryable(primaryEx) && backup != null && !backup.isBlank()
                    && !backup.equalsIgnoreCase(primary);
            // Không ghi nội dung trả về vì có thể chứa số CCCD
            log.warn("Citizen ID OCR failed on model '{}': {}. Retryable: {}", primary,
                    GeminiErrors.describe(primaryEx), canRetry);
            if (canRetry) {
                try {
                    String key = backupApiKey != null && !backupApiKey.isBlank() ? backupApiKey : apiKey;
                    Extracted x = readWith(imageBytes, backup, key);
                    log.info("Citizen ID OCR succeeded on backup model '{}'", backup);
                    return x;
                } catch (Exception backupEx) {
                    log.warn("Citizen ID OCR backup model '{}' also failed: {}", backup,
                            GeminiErrors.describe(backupEx));
                }
            }
            return readWithFallback(imageBytes, c);
        }
    }

    /** Gemini không dùng được: đọc bằng Groq vision (DeepSeek không đọc được ảnh). Không có thì báo không đọc được. */
    private Extracted readWithFallback(byte[] imageBytes, AiFeatureConfig c) {
        if (!fallbackReady(c)) {
            throw unavailable();
        }
        try {
            String raw = fallback.visionJson(c, PROMPT, List.of(AiImages.shrinkToJpeg(imageBytes)), 512);
            Extracted x = parse(FallbackAiClient.jsonObject(raw));
            log.info("Citizen ID OCR succeeded on fallback Groq model '{}'", fallback.visionModelName(c));
            return x;
        } catch (Exception e) {
            log.warn("Citizen ID OCR fallback ({}) failed: {}", fallback.visionModelName(c), GeminiErrors.describe(e));
            throw unavailable();
        }
    }

    private Extracted readWith(byte[] imageBytes, String targetModel, String targetApiKey) throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));
        RestClient client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", targetApiKey)
                .requestFactory(factory)
                .build();

        String prompt = PROMPT;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inline_data", Map.of(
                                "mime_type", UploadedDocumentValidator.detectContentType(imageBytes),
                                "data", Base64.getEncoder().encodeToString(imageBytes)))))),
                "generationConfig", Map.of(
                        "temperature", 0.0,
                        "maxOutputTokens", 512,
                        "responseMimeType", "application/json",
                        "thinkingConfig", Map.of("thinkingBudget", 0)));

        byte[] rawBytes = client.post()
                .uri("/models/{model}:generateContent", targetModel)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                // Đọc byte rồi tự parse: Gemini có lúc trả Content-Type application/octet-stream cho body JSON
                .body(byte[].class);
        // JSON luôn là UTF-8; không để Spring đoán charset theo Content-Type (octet-stream -> ISO-8859-1 làm hỏng tiếng Việt)
        String rawBody = rawBytes == null ? null : new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode res = rawBody == null || rawBody.isBlank() ? null : objectMapper.readTree(rawBody);
        String json = res == null ? null
                : res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString(null);
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Empty response from Gemini");
        }
        return parse(json);
    }

    Extracted parse(String json) throws Exception {
        JsonNode n = objectMapper.readTree(json);
        return new Extracted(text(n, "citizenId"), text(n, "fullName"), text(n, "dateOfBirth"),
                text(n, "gender"), text(n, "placeOfResidence"), text(n, "documentType"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asString("").trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static UserProfileException unavailable() {
        return new UserProfileException(ErrorCode.IDENTITY_OCR_UNAVAILABLE,
                "Could not read the card image right now; please scan the QR code or enter the details manually");
    }
}
