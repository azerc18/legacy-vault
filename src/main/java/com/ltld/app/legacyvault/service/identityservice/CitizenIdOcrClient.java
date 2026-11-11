package com.ltld.app.legacyvault.service.identityservice;

import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Value("${app.ai.provider:mock}")
    private String aiProvider;

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.model:gemini-3.6-flash}")
    private String model;

    public boolean isAvailable() {
        return "gemini".equalsIgnoreCase(aiProvider) && apiKey != null && !apiKey.isBlank();
    }

    public Extracted read(byte[] imageBytes) {
        if (!isAvailable()) {
            throw unavailable();
        }
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
            factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));
            RestClient client = RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                    .defaultHeader("x-goog-api-key", apiKey)
                    .requestFactory(factory)
                    .build();

            String prompt = "Ảnh đính kèm là mặt trước Căn cước công dân / Căn cước Việt Nam. "
                    + "CHỈ trích xuất thông tin thực sự đọc được; trường nào không đọc được thì trả null, "
                    + "TUYỆT ĐỐI không đoán hay bịa. Trả về đúng JSON với các trường: "
                    + "documentType (\"CCCD\" nếu đúng là thẻ căn cước Việt Nam, ngược lại \"OTHER\"), "
                    + "citizenId (12 chữ số, không khoảng trắng), fullName (giữ nguyên dấu), "
                    + "dateOfBirth (dd/MM/yyyy), gender (\"Nam\" hoặc \"Nữ\"), "
                    + "placeOfResidence (nơi thường trú / nơi cư trú, nguyên văn).";

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

            JsonNode res = client.post()
                    .uri("/models/{model}:generateContent", model)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String json = res == null ? null
                    : res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString(null);
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Empty response from Gemini");
            }
            return parse(json);
        } catch (UserProfileException e) {
            throw e;
        } catch (Exception e) {
            // Không ghi nội dung trả về vì có thể chứa số CCCD
            log.warn("Citizen ID OCR failed: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
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
