package com.ltld.app.legacyvault.service.ocrservice;

import com.ltld.app.legacyvault.dto.ocrdto.DeathCertificateOcrResponseDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.identityservice.OwnerIdentityService;
import com.ltld.app.legacyvault.enums.OwnerIdMatch;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAccessGuard;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.service.legalverificationservice.VerifierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeathCertificateOcrServiceImpl implements DeathCertificateOcrService {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 25000;
    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final int MAX_CALLS_PER_MINUTE = 5;
    private static final long CACHE_TTL_SECONDS = 30 * 60;
    /** Quá ngưỡng này thì dọn key hết hạn để map trong bộ nhớ không phình mãi. */
    private static final int MAX_IN_MEMORY_KEYS = 500;

    public static final String CAT_VALID_DEATH_CERT = "VALID_DEATH_CERTIFICATE";
    public static final String CAT_RELATED_DOC = "RELATED_SUPPORTING_DOCUMENT";
    public static final String CAT_UNRELATED_DOC = "UNRELATED_DOCUMENT";

    public static final String ENGINE_GEMINI = "GEMINI_VISION_AI";
    public static final String ENGINE_DEMO = "DEMO_SIMULATION";
    public static final String ENGINE_UNAVAILABLE = "UNAVAILABLE";

    public static final String DEFAULT_UNAVAILABLE_MESSAGE =
            "Chưa phân tích được bằng AI, vui lòng tự kiểm tra";

    private final ExecutorAccessGuard executorAccessGuard;
    private final VerifierAccessGuard verifierAccessGuard;
    private final UploadedDocumentValidator uploadedDocumentValidator;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final PlatformTransactionManager transactionManager;
    private final OwnerIdentityService ownerIdentityService;

    private final Map<String, CachedOcrEntry> verifierCache = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();

    @Value("${app.ai.provider:mock}")
    private String aiProvider;

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.model:gemini-3.6-flash}")
    private String model;

    /**
     * Mặc định false trên môi trường thật để tên file không bao giờ điều khiển được kết quả
     * và không trả dữ liệu bịa khi AI tắt/lỗi. Chỉ bật khi cần trình diễn kịch bản mô phỏng.
     */
    @Value("${app.ai.ocr.demo-scenarios:false}")
    private boolean demoScenariosEnabled;

    /*
     * Không đặt @Transactional cho cả hàm: lời gọi Gemini có thể mất tới 25 giây, giữ transaction
     * suốt lúc đó sẽ chiếm 1 kết nối DB và dễ làm cạn pool Hikari khi nhiều người quét cùng lúc.
     * Chỉ kiểm quyền và đọc dữ liệu cần thiết trong một transaction ngắn (chỉ đọc), phần đọc file,
     * gọi AI và ghi audit chạy ngoài transaction (AuditEventListener có fallbackExecution = true).
     */
    @Override
    public DeathCertificateOcrResponseDto analyzeForExecutor(UUID assignmentId, String executorLogin, MultipartFile file) {
        ExecutorContext ctx = readOnlyTx().execute(status -> {
            ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);
            return new ExecutorContext(
                    ownerNameOf(assignment.getOwner() != null ? assignment.getOwner().getFullName() : null),
                    assignment.getExecutorUser() != null ? assignment.getExecutorUser().getId() : null,
                    assignment.getOwner() != null ? assignment.getOwner().getId() : null);
        });
        byte[] plainBytes = uploadedDocumentValidator.readAndValidate(file);
        String ownerFullName = ctx.ownerFullName();
        UUID actorId = ctx.actorId();
        String rateKey = "executor:" + (actorId != null ? actorId : executorLogin);

        if (!allowCall(rateKey)) {
            return buildUnavailableResult(ownerFullName,
                    "Bạn đã yêu cầu phân tích AI quá số lần cho phép trong 1 phút. Vui lòng tự kiểm tra hoặc thử lại sau.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "death-certificate.pdf";
        DeathCertificateOcrResponseDto result = analyzeBytes(plainBytes, originalFilename, ownerFullName);
        attachOwnerIdMatch(result, ctx.ownerId());

        recordAudit(actorId, executorLogin, "ExecutorAssignment", assignmentId.toString(), result);
        return result;
    }

    @Override
    public DeathCertificateOcrResponseDto analyzeForVerifier(UUID requestId, UUID verifierId) {
        VerifierContext ctx = readOnlyTx().execute(status -> {
            LegalVerificationRequest request = verifierAccessGuard.requireReadable(requestId, verifierId);
            return new VerifierContext(
                    ownerNameOf(request.getOwner() != null ? request.getOwner().getFullName() : null),
                    request.getDeathCertificateFileUrlEncrypted(),
                    request.getOwner() != null ? request.getOwner().getId() : null);
        });
        String ownerFullName = ctx.ownerFullName();
        String cacheKey = requestId + ":" + String.valueOf(ctx.fileUrlEncrypted());

        CachedOcrEntry cached = verifierCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            recordAudit(verifierId, null, "LegalVerificationRequest", requestId.toString(), cached.response());
            return cached.response();
        }

        String rateKey = "verifier:" + verifierId;
        if (!allowCall(rateKey)) {
            return buildUnavailableResult(ownerFullName,
                    "Bạn đã yêu cầu phân tích AI quá số lần cho phép trong 1 phút. Vui lòng tự kiểm tra hoặc thử lại sau.");
        }

        byte[] plainBytes = readDecryptedDocument(ctx.fileUrlEncrypted(), requestId);
        String documentRef = "verification-request-" + requestId;
        DeathCertificateOcrResponseDto result = analyzeBytes(plainBytes, documentRef, ownerFullName);
        attachOwnerIdMatch(result, ctx.ownerId());

        if (ENGINE_GEMINI.equals(result.getEngineSource())) {
            if (verifierCache.size() >= MAX_IN_MEMORY_KEYS) {
                verifierCache.values().removeIf(CachedOcrEntry::isExpired);
            }
            verifierCache.put(cacheKey, new CachedOcrEntry(result, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        }

        recordAudit(verifierId, null, "LegalVerificationRequest", requestId.toString(), result);
        return result;
    }

    private TransactionTemplate readOnlyTx() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx;
    }

    private static String ownerNameOf(String fullName) {
        return fullName != null ? fullName : "Chủ két";
    }

    private record ExecutorContext(String ownerFullName, UUID actorId, UUID ownerId) {}

    private record VerifierContext(String ownerFullName, String fileUrlEncrypted, UUID ownerId) {}

    /**
     * KYC: đối chiếu số CCCD / CMND AI đọc được trên giấy chứng tử với CCCD Owner đã xác minh (so HMAC, không giải mã).
     * Chỉ là gợi ý cho Executor / Legal Verifier, không tự quyết định. Lỗi tra cứu thì bỏ qua, không làm hỏng kết quả OCR.
     */
    private void attachOwnerIdMatch(DeathCertificateOcrResponseDto result, UUID ownerId) {
        if (result == null || ENGINE_UNAVAILABLE.equals(result.getEngineSource())) {
            return;
        }
        OwnerIdMatch match;
        try {
            match = ownerIdentityService.matchDocumentId(ownerId, result.getIdNumber());
        } catch (RuntimeException e) {
            log.warn("Owner ID match failed for owner {}: {}", ownerId, e.getClass().getSimpleName());
            return;
        }
        if (match != null) {
            applyOwnerIdMatch(result, match);
        }
    }

    /**
     * Đưa kết quả đối chiếu CCCD vào cảnh báo, điểm tin cậy và gợi ý từ chối (giống cách xử lý họ tên lệch).
     * Không in số CCCD của Owner ra kết quả.
     */
    static void applyOwnerIdMatch(DeathCertificateOcrResponseDto result, OwnerIdMatch match) {
        result.setOwnerIdMatch(match.name());
        List<String> warnings = result.getWarnings() == null ? new ArrayList<>() : new ArrayList<>(result.getWarnings());
        switch (match) {
            case MATCHED -> { /* bằng chứng mạnh, không cần cảnh báo */ }
            case MISMATCHED -> {
                // Họ tên trùng nhưng số giấy tờ khác: rất có thể là người khác trùng tên, hoặc giấy bị sửa
                warnings.add(0, "LƯU Ý ĐỊNH DANH: Số CCCD/CMND trên tài liệu (" + result.getIdNumber()
                        + ") không khớp với CCCD Chủ két đã xác minh trong hệ thống.");
                if (result.getSuggestedRejectionCode() == null
                        || !"SUSPECTED_FORGERY".equals(result.getSuggestedRejectionCode())) {
                    result.setSuggestedRejectionCode("INFO_MISMATCH");
                }
                if (result.getConfidenceScore() != null) {
                    result.setConfidenceScore(Math.min(result.getConfidenceScore(), 40));
                }
                if (result.getDeathProofRelevancePercent() != null) {
                    result.setDeathProofRelevancePercent(Math.min(result.getDeathProofRelevancePercent(), 40));
                }
                result.setExecutorSuggestion("Cảnh báo cố vấn: Số CCCD/CMND trên giấy không khớp với CCCD Chủ két đã xác minh. "
                        + "Hãy kiểm tra lại bạn có tải đúng giấy tờ của Chủ két không. "
                        + (result.getExecutorSuggestion() == null ? "" : result.getExecutorSuggestion()));
                result.setVerifierSuggestion("Gợi ý cho Verifier: Số CCCD/CMND trên giấy không khớp với CCCD Chủ két đã xác minh, "
                        + "cân nhắc lý do \"Thông tin không khớp\". "
                        + (result.getVerifierSuggestion() == null ? "" : result.getVerifierSuggestion()));
            }
            case NOT_ON_DOCUMENT -> warnings.add("LƯU Ý ĐỊNH DANH: Không đọc được số CCCD/CMND trên tài liệu, "
                    + "chưa đối chiếu được với CCCD Chủ két; chỉ đối chiếu theo họ tên.");
            case UNVERIFIABLE -> warnings.add("LƯU Ý ĐỊNH DANH: Số giấy tờ trên tài liệu không đối chiếu được "
                    + "(Chủ két không khai CMND cũ, hoặc số không đúng định dạng 9/12 chữ số).");
            case OWNER_NOT_VERIFIED -> warnings.add("LƯU Ý ĐỊNH DANH: Chủ két chưa xác minh CCCD trong hệ thống "
                    + "nên chỉ đối chiếu được theo họ tên.");
        }
        result.setWarnings(warnings);
    }

    private void recordAudit(UUID actorId, String email, String targetType, String targetId, DeathCertificateOcrResponseDto result) {
        if (auditLogService == null) {
            return;
        }
        AuditResult auditResult = ENGINE_UNAVAILABLE.equals(result.getEngineSource())
                ? AuditResult.FAILURE
                : AuditResult.SUCCESS;
        auditLogService.log(
                AuditAction.LEGAL_DOCUMENT_OCR_ANALYZED,
                auditResult,
                actorId,
                email,
                targetType,
                targetId,
                "engine=" + result.getEngineSource()
        );
    }

    boolean allowCall(String key) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        if (rateLimitBuckets.size() >= MAX_IN_MEMORY_KEYS) {
            pruneStaleBuckets(windowStart);
        }
        Deque<Long> timestamps = rateLimitBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_CALLS_PER_MINUTE) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /** Xoá các bucket không còn lượt gọi nào trong cửa sổ 1 phút. */
    private void pruneStaleBuckets(long windowStart) {
        rateLimitBuckets.entrySet().removeIf(e -> {
            Deque<Long> timestamps = e.getValue();
            synchronized (timestamps) {
                Long last = timestamps.peekLast();
                return last == null || last < windowStart;
            }
        });
    }

    private byte[] readDecryptedDocument(String fileUrlEncrypted, UUID requestId) {
        try {
            return cryptoService.decryptBytes(fileStorageService.readFile(fileUrlEncrypted));
        } catch (StorageUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Death certificate read/decrypt failed for request {}", requestId, e);
            throw new IllegalStateException("Could not read the document. Please try again later.");
        }
    }

    DeathCertificateOcrResponseDto analyzeBytes(byte[] plainBytes, String fileName, String expectedOwnerName) {
        if (demoScenariosEnabled && isDemoScenarioFilename(fileName)) {
            return buildDemoSimulationResult(plainBytes, fileName, expectedOwnerName);
        }

        if ("gemini".equalsIgnoreCase(aiProvider) && apiKey != null && !apiKey.isBlank()) {
            try {
                return callGeminiVisionOcr(plainBytes, expectedOwnerName);
            } catch (Exception ex) {
                log.warn("Gemini Vision OCR failed or timed out for document '{}': {}", fileName, ex.getMessage());
                return buildUnavailableResult(expectedOwnerName, DEFAULT_UNAVAILABLE_MESSAGE);
            }
        }

        if (demoScenariosEnabled) {
            return buildDemoSimulationResult(plainBytes, fileName, expectedOwnerName);
        }

        return buildUnavailableResult(expectedOwnerName, DEFAULT_UNAVAILABLE_MESSAGE);
    }

    private static boolean isDemoScenarioFilename(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalizedFile = fileName.toLowerCase(Locale.ROOT);
        return normalizedFile.contains("mismatch")
                || normalizedFile.contains("sai_ten")
                || normalizedFile.contains("khong_khop")
                || normalizedFile.contains("giay_bao_tu")
                || normalizedFile.contains("benh_an")
                || normalizedFile.contains("cccd")
                || normalizedFile.contains("di_chuc")
                || normalizedFile.contains("mo_nhoe")
                || normalizedFile.contains("blur")
                || normalizedFile.contains("gia_mao")
                || normalizedFile.contains("nam_sinh")
                || normalizedFile.contains("noi_o")
                || normalizedFile.contains("forged");
    }

    static DeathCertificateOcrResponseDto buildUnavailableResult(String expectedOwnerName, String message) {
        String msg = (message == null || message.isBlank()) ? DEFAULT_UNAVAILABLE_MESSAGE : message;
        return DeathCertificateOcrResponseDto.builder()
                .engineSource(ENGINE_UNAVAILABLE)
                .unavailableMessage(msg)
                .expectedOwnerName(expectedOwnerName)
                .ownerNameMatched(false)
                .hasOfficialSeal(false)
                .warnings(List.of(msg))
                .build();
    }

    private DeathCertificateOcrResponseDto callGeminiVisionOcr(byte[] plainBytes, String expectedOwnerName) throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MS));

        RestClient restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(factory)
                .build();

        String mimeType = UploadedDocumentValidator.detectContentType(plainBytes);
        String base64Data = Base64.getEncoder().encodeToString(plainBytes);

        String prompt = "Bạn là hệ thống AI Cố vấn Thẩm định Tài liệu Pháp lý của LegacyVault. "
                + "Bạn CHỈ đưa ra đánh giá, phân tích và gợi ý tham khảo cho Người thừa hành (Executor) và Cán bộ xác minh (Legal Verifier), không quyết định thay con người.\n"
                + "QUY TẮC TUYỆT ĐỐI: Chỉ trích xuất thông tin thực sự đọc được trong tài liệu. Nếu trường nào không có hoặc không đọc được, BẮT BUỘC trả về null cho trường đó, TUYỆT ĐỐI KHÔNG tự bịa số hiệu, ngày tháng, họ tên hay cơ quan cấp.\n"
                + "Hãy phân tích tài liệu đính kèm và đối chiếu với tên Chủ két dự kiến: \"" + expectedOwnerName + "\".\n"
                + "Trả về đúng JSON gồm các trường:\n"
                + "- documentCategory: \"VALID_DEATH_CERTIFICATE\" (Trích lục khai tử/Giấy chứng tử UBND) | \"RELATED_SUPPORTING_DOCUMENT\" (Giấy báo tử bệnh viện, CCCD, Hồ sơ bệnh án, Di chúc...) | \"UNRELATED_DOCUMENT\"\n"
                + "- documentType: tên loại văn bản tiếng Việt (hoặc null nếu không xác định được)\n"
                + "- detectedHeaderTitle: trích nguyên văn dòng tiêu đề chính bên trong tài liệu (hoặc null nếu không có)\n"
                + "- titleAssessment: đánh giá ngắn gọn về tiêu đề bên trong tài liệu\n"
                + "- imageQualityScore: số nguyên 0-100 đánh giá chất lượng hình ảnh, độ rõ nét, độ tương phản\n"
                + "- imageClarityLabel: nhận xét độ rõ nét\n"
                + "- deathProofRelevancePercent: số nguyên 0-100 đánh giá nội dung tài liệu chứng minh được sự qua đời của Chủ két ở mức độ bao nhiêu %\n"
                + "- deathProofExplanation: giải thích ngắn gọn mức độ chứng minh sự qua đời\n"
                + "- executorSuggestion: lời khuyên tham khảo cho Người thừa hành (Executor)\n"
                + "- verifierSuggestion: lời khuyên tham khảo cho Cán bộ xác minh (Legal Verifier)\n"
                + "- certificateNumber, bookNumber, deceasedFullName, dateOfBirth, gender, idNumber, dateOfDeath, placeOfDeath, causeOfDeath, issuingAuthority, issueDate, signerName (strings hoặc null nếu không đọc được)\n"
                + "- hasOfficialSeal (boolean: chỉ true khi nhìn thấy rõ con dấu đỏ thực tế trên tài liệu, ngược lại là false), confidenceScore (int 0-100), warnings (array of strings).";

        Map<String, Object> inlineData = Map.of(
                "mime_type", mimeType,
                "data", base64Data
        );
        Map<String, Object> userPartText = Map.of("text", prompt);
        Map<String, Object> userPartMedia = Map.of("inline_data", inlineData);
        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(userPartText, userPartMedia)
        );
        Map<String, Object> generationConfig = Map.of(
                "temperature", 0.1,
                "maxOutputTokens", MAX_OUTPUT_TOKENS,
                "responseMimeType", "application/json",
                "thinkingConfig", Map.of("thinkingBudget", 0)
        );
        Map<String, Object> body = Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );

        GeminiResponse res = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);

        if (res == null || res.candidates() == null || res.candidates().isEmpty()) {
            throw new IllegalStateException("Empty candidates from Gemini Vision API");
        }
        var candidateContent = res.candidates().get(0).content();
        if (candidateContent == null || candidateContent.parts() == null || candidateContent.parts().isEmpty()) {
            throw new IllegalStateException("Empty content parts from Gemini Vision API");
        }
        String rawJson = candidateContent.parts().get(0).text();
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("Empty JSON text from Gemini Vision API");
        }

        return parseGeminiJson(rawJson, expectedOwnerName);
    }

    /**
     * Phân tích JSON trả về từ Gemini Vision API.
     * Trường thiếu sẽ giữ nguyên là null (không bao giờ điền giá trị bịa),
     * hasOfficialSeal mặc định false, ownerNameMatched mặc định false nếu không đọc được tên.
     */
    DeathCertificateOcrResponseDto parseGeminiJson(String rawJson, String expectedOwnerName) throws Exception {
        JsonNode extracted = objectMapper.readTree(rawJson);

        String rawCategory = textOrNull(extracted, "documentCategory");
        String category;
        if (CAT_VALID_DEATH_CERT.equals(rawCategory)
                || CAT_RELATED_DOC.equals(rawCategory)
                || CAT_UNRELATED_DOC.equals(rawCategory)) {
            category = rawCategory;
        } else {
            category = CAT_UNRELATED_DOC;
        }

        String docType = textOrNull(extracted, "documentType");
        String detectedTitle = textOrNull(extracted, "detectedHeaderTitle");
        String titleAssess = textOrNull(extracted, "titleAssessment");

        Integer imgQuality = intOrNull(extracted, "imageQualityScore");
        String imgClarity = textOrNull(extracted, "imageClarityLabel");

        String deceasedName = textOrNull(extracted, "deceasedFullName");
        boolean matched = deceasedName != null && isNameMatching(deceasedName, expectedOwnerName);
        boolean hasSeal = extracted.path("hasOfficialSeal").isBoolean()
                && extracted.path("hasOfficialSeal").asBoolean(false);

        // AI không trả thì để null (giao diện hiện "không có"), không tự đoán một con số.
        Integer deathProofPct = intOrNull(extracted, "deathProofRelevancePercent");

        String deathProofExp = textOrNull(extracted, "deathProofExplanation");
        if (deathProofExp == null) {
            deathProofExp = buildDefaultDeathProofExplanation(category, matched, deceasedName, expectedOwnerName);
        }

        String execSuggest = textOrNull(extracted, "executorSuggestion");
        if (execSuggest == null) {
            execSuggest = buildDefaultExecutorSuggestion(category, matched, imgQuality, docType);
        }
        String verifSuggest = textOrNull(extracted, "verifierSuggestion");
        if (verifSuggest == null) {
            verifSuggest = buildDefaultVerifierSuggestion(category, matched, imgQuality, docType, expectedOwnerName);
        }

        List<String> warnings = new ArrayList<>();
        JsonNode warningsNode = extracted.path("warnings");
        if (warningsNode.isArray()) {
            for (JsonNode w : warningsNode) {
                if (w != null && !w.isNull() && !w.asString("").isBlank()) {
                    warnings.add(w.asString().trim());
                }
            }
        }

        String suggestedRejection = null;

        if (imgQuality != null && imgQuality < 60) {
            suggestedRejection = "DOCUMENT_UNREADABLE";
            warnings.add("GỢI Ý CHẤT LƯỢNG ẢNH: Độ rõ nét của tài liệu thấp (" + imgQuality
                    + "%). Người xác minh nên kiểm tra kỹ bằng mắt thường hoặc yêu cầu bản chụp rõ hơn.");
        }
        if (CAT_RELATED_DOC.equals(category)) {
            if (suggestedRejection == null) suggestedRejection = "DOCUMENT_INVALID";
            warnings.add("LƯU Ý TIÊU ĐỀ & LOẠI VĂN BẢN: Tài liệu được nhận diện là văn bản liên quan ("
                    + (docType != null ? docType : "Chưa rõ loại") + "), chưa phải Trích lục khai tử chính thức của UBND.");
        } else if (CAT_UNRELATED_DOC.equals(category)) {
            if (suggestedRejection == null) suggestedRejection = "DOCUMENT_INVALID";
            warnings.add("LƯU Ý LOẠI VĂN BẢN: Tài liệu không phải là Trích lục khai tử / Giấy chứng tử hợp lệ.");
        }
        if (deceasedName == null) {
            if (suggestedRejection == null) suggestedRejection = "DOCUMENT_UNREADABLE";
            warnings.add("LƯU Ý ĐỊNH DANH: Không đọc được họ và tên người mất trên tài liệu.");
        } else if (!matched) {
            if (suggestedRejection == null) suggestedRejection = "INFO_MISMATCH";
            warnings.add("LƯU Ý ĐỊNH DANH: Họ tên trích xuất trong tài liệu (" + deceasedName
                    + ") không khớp với tên Chủ két trong hệ thống (" + expectedOwnerName + ").");
        }
        if (!hasSeal) {
            if (suggestedRejection == null) suggestedRejection = "DOCUMENT_INCOMPLETE";
            warnings.add("LƯU Ý HÌNH THỨC: Chưa phát hiện rõ con dấu đỏ chứng thực trên tài liệu.");
        }

        String certNo = textOrNull(extracted, "certificateNumber");
        String bookNo = textOrNull(extracted, "bookNumber");
        String dobStr = textOrNull(extracted, "dateOfBirth");
        String genderStr = textOrNull(extracted, "gender");
        String idNumberStr = textOrNull(extracted, "idNumber");
        String deathDate = textOrNull(extracted, "dateOfDeath");
        String placeStr = textOrNull(extracted, "placeOfDeath");
        String causeStr = textOrNull(extracted, "causeOfDeath");
        String authority = textOrNull(extracted, "issuingAuthority");
        String issueDate = textOrNull(extracted, "issueDate");
        String signer = textOrNull(extracted, "signerName");

        int score = calculateWeightedScore(category, matched, hasSeal, extracted);

        String cccdMismatchWarning = detectInternalCccdBirthYearMismatch(dobStr, genderStr, idNumberStr);
        if (cccdMismatchWarning != null) {
            suggestedRejection = "SUSPECTED_FORGERY";
            deathProofPct = deathProofPct != null ? Math.min(deathProofPct, 38) : null;
            score = Math.min(score, 42);
            warnings.add(0, cccdMismatchWarning);
            deathProofExp = "Dù Họ tên trùng khớp với Chủ két (" + expectedOwnerName
                    + "), nhưng phát hiện mâu thuẫn nội tại giữa Năm sinh và cấu trúc Số CCCD 12 số trên chính tờ giấy.";
            execSuggest = "Cảnh báo cố vấn: Họ tên khớp nhưng Năm sinh (" + dobStr + ") mâu thuẫn với mã năm sinh trong số CCCD (" + idNumberStr
                    + "). Bạn nên kiểm tra lại tính xác thực của bản gốc hoặc đính kèm thêm ảnh chụp CCCD của người mất để giải trình.";
            verifSuggest = "Gợi ý cho Verifier: Phát hiện dấu hiệu bất thường nội tại (Năm sinh lệch với quy chuẩn mã số CCCD). Đề xuất yêu cầu Executor gửi thêm ảnh CCCD/Hộ khẩu qua khung Trao đổi (FR-11) để đối chiếu, hoặc cân nhắc lý do \"Nghi ngờ tài liệu bị làm giả\".";
        }

        String summaryNote = buildSummaryNote(category, docType, certNo, deceasedName, deathDate, authority, matched, expectedOwnerName, deathProofPct, imgQuality);

        return DeathCertificateOcrResponseDto.builder()
                .documentCategory(category)
                .documentType(docType)
                .detectedHeaderTitle(detectedTitle)
                .titleAssessment(titleAssess)
                .imageQualityScore(imgQuality)
                .imageClarityLabel(imgClarity)
                .deathProofRelevancePercent(deathProofPct)
                .deathProofExplanation(deathProofExp)
                .executorSuggestion(execSuggest)
                .verifierSuggestion(verifSuggest)
                .certificateNumber(certNo)
                .bookNumber(bookNo)
                .deceasedFullName(deceasedName)
                .dateOfBirth(dobStr)
                .gender(genderStr)
                .idNumber(idNumberStr)
                .dateOfDeath(deathDate)
                .placeOfDeath(placeStr)
                .causeOfDeath(causeStr)
                .issuingAuthority(authority)
                .issueDate(issueDate)
                .signerName(signer)
                .hasOfficialSeal(hasSeal)
                .ownerNameMatched(matched)
                .expectedOwnerName(expectedOwnerName)
                .confidenceScore(score)
                .suggestedRejectionCode(suggestedRejection)
                .engineSource(ENGINE_GEMINI)
                .summaryNote(summaryNote)
                .warnings(warnings)
                .build();
    }

    /**
     * Chỉ được gọi khi cờ cấu hình app.ai.ocr.demo-scenarios=true.
     * Gắn nhãn rõ ràng engineSource = DEMO_SIMULATION để người dùng biết là dữ liệu mô phỏng.
     */
    private DeathCertificateOcrResponseDto buildDemoSimulationResult(byte[] plainBytes, String fileName, String expectedOwnerName) {
        String normalizedFile = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        boolean simulateMismatch = normalizedFile.contains("mismatch")
                || normalizedFile.contains("sai_ten")
                || normalizedFile.contains("khong_khop");
        boolean simulateRelatedDoc = normalizedFile.contains("giay_bao_tu")
                || normalizedFile.contains("benh_an")
                || normalizedFile.contains("cccd")
                || normalizedFile.contains("di_chuc")
                || normalizedFile.contains("related");
        boolean simulateBlurry = normalizedFile.contains("mo_nhoe")
                || normalizedFile.contains("blur")
                || normalizedFile.contains("low_quality");
        boolean simulateForgedSmallDetail = normalizedFile.contains("gia_mao")
                || normalizedFile.contains("nam_sinh")
                || normalizedFile.contains("noi_o")
                || normalizedFile.contains("forged");

        int hash = Math.abs(Arrays.hashCode(plainBytes));
        int certSeq = (hash % 850) + 101;
        String cleanExpectedOwner = (expectedOwnerName == null || expectedOwnerName.isBlank())
                ? "Trần Long Vân"
                : expectedOwnerName.trim();

        String category = simulateRelatedDoc ? CAT_RELATED_DOC : CAT_VALID_DEATH_CERT;
        String docType = simulateRelatedDoc
                ? "Giấy báo tử y tế (Bệnh viện cấp - Tài liệu liên quan)"
                : "Trích lục khai tử (Mẫu TP/HT-2024-TLKT)";
        String detectedTitle = simulateRelatedDoc
                ? "GIẤY BÁO TỬ (BỆNH VIỆN ĐẠI HỌC Y DƯỢC TP.HCM)"
                : "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM — TRÍCH LỤC KHAI TỬ (BẢN SAO)";
        String titleAssess = simulateRelatedDoc
                ? "Tiêu đề văn bản y tế xác nhận tử vong tại bệnh viện (Chưa phải tiêu đề Trích lục khai tử của UBND)"
                : "Tiêu đề khớp chuẩn Quốc hiệu & Mẫu Trích lục khai tử của Bộ Tư pháp";

        int imgQuality = simulateBlurry ? 48 : 96;
        String imgClarity = simulateBlurry
                ? "Ảnh hơi mờ / Độ phân giải thấp (48%) — Một số ký tự nhỏ và viền con dấu bị nhòe"
                : "Rõ nét cao (96%) — Độ tương phản tốt, chữ in và dấu đỏ hiển thị sắc nét";

        String certNo = simulateRelatedDoc
                ? String.format("%03d/GBT-BV", certSeq)
                : String.format("%03d/TLKT-BS", certSeq);
        String bookNo = simulateRelatedDoc
                ? "HSBA-2026/BVĐHYD"
                : String.format("02/%d/TP-HT", LocalDate.now().getYear());

        String deceasedName = simulateMismatch
                ? "NGUYỄN VĂN KHÁC"
                : cleanExpectedOwner.toUpperCase(Locale.forLanguageTag("vi"));
        boolean matched = !simulateMismatch;

        String dobStr = simulateForgedSmallDetail ? "18/04/1975" : "18/04/1965";
        String genderStr = "Nam";
        String idNumberStr = String.format("07906500%04d", (hash % 9000) + 1000);
        String placeStr = simulateForgedSmallDetail
                ? "Số 42 Trần Phú, Quận Hải Châu, TP. Đà Nẵng (Lệch mã vùng CCCD 079 - TP.HCM)"
                : "Bệnh viện Đại học Y Dược TP. Hồ Chí Minh";

        int deathProofPct = 95;
        if (!matched) {
            deathProofPct = 15;
        } else if (simulateForgedSmallDetail) {
            deathProofPct = 38;
        } else if (simulateRelatedDoc) {
            deathProofPct = 76;
        }

        String deathProofExp = buildDefaultDeathProofExplanation(category, matched, deceasedName, cleanExpectedOwner);
        String execSuggest = buildDefaultExecutorSuggestion(category, matched, imgQuality, docType);
        String verifSuggest = buildDefaultVerifierSuggestion(category, matched, imgQuality, docType, cleanExpectedOwner);

        LocalDate today = LocalDate.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String deathDateStr = "08:30 ngày " + today.minusDays(5).format(dtf);
        String issueDateStr = today.minusDays(2).format(dtf);
        String authority = simulateRelatedDoc
                ? "Bệnh viện Đại học Y Dược TP. Hồ Chí Minh"
                : "UBND Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh";

        List<String> warnings = new ArrayList<>();
        String suggestedRejection = null;

        String cccdMismatchWarning = detectInternalCccdBirthYearMismatch(dobStr, genderStr, idNumberStr);
        if (cccdMismatchWarning != null) {
            suggestedRejection = "SUSPECTED_FORGERY";
            warnings.add(cccdMismatchWarning);
            warnings.add("PHÂN TÍCH CHI TIẾT NHỎ (FORENSICS): Tài liệu có tiêu đề chuẩn, ảnh rõ nét (96%) và đúng Họ tên ("
                    + deceasedName + "), nhưng dòng Năm sinh (" + dobStr + ") và Nơi ghi nhận có dấu hiệu bị sửa đổi hoặc lấy từ giấy chứng tử của người khác trùng họ tên.");
            deathProofExp = "Dù Họ tên trùng khớp với Chủ két (" + cleanExpectedOwner
                    + ") và ảnh rõ nét, nhưng phát hiện mâu thuẫn nội tại giữa Năm sinh (1975) và cấu trúc Số CCCD 12 số (079065... = sinh năm 1965). Chỉ đạt 38% độ tin cậy.";
            execSuggest = "Cảnh báo cố vấn: Tài liệu đúng tên Chủ két và rõ nét, NHƯNG Năm sinh (" + dobStr
                    + ") mâu thuẫn với mã năm sinh ẩn trong số CCCD (" + idNumberStr
                    + "). Bạn vẫn có thể nộp nếu muốn, nhưng nên kiểm tra kỹ hoặc gửi kèm ảnh CCCD gốc để giải trình.";
            verifSuggest = "Gợi ý cho Verifier: Phát hiện mâu thuẫn nội tại tinh vi! Dù đúng tên " + cleanExpectedOwner
                    + " và đúng mẫu Trích lục khai tử, nhưng Năm sinh (1975) không khớp với quy chuẩn mã năm sinh trong số CCCD (079065... = 1965). Đề xuất yêu cầu Executor gửi ảnh chụp CCCD người mất qua khung Trao đổi (FR-11) để kiểm chứng, hoặc tham khảo lý do \"Nghi ngờ tài liệu bị làm giả\".";
        }
        if (simulateBlurry) {
            if (suggestedRejection == null) suggestedRejection = "DOCUMENT_UNREADABLE";
            warnings.add("LƯU Ý ĐỘ RÕ NÉT: Chất lượng hình ảnh chỉ đạt 48% (hơi mờ). Khuyến nghị kiểm tra kỹ bằng mắt thường hoặc tải lại bản quét rõ nét hơn.");
        }
        if (simulateRelatedDoc) {
            if (suggestedRejection == null) suggestedRejection = "DOCUMENT_INVALID";
            warnings.add("ĐÁNH GIÁ TIÊU ĐỀ & NỘI DUNG: Tiêu đề bên trong file là \"" + detectedTitle
                    + "\". Nội dung chứng minh sự kiện qua đời đạt " + deathProofPct
                    + "% (xác nhận tử vong tại cơ sở y tế), nhưng đây là tài liệu liên quan bổ trợ cho thủ tục khai tử.");
        }
        if (!matched) {
            if (suggestedRejection == null) suggestedRejection = "INFO_MISMATCH";
            warnings.add("LƯU Ý ĐỐI CHIẾU ĐỊNH DANH: Họ tên người mất trong tài liệu (" + deceasedName
                    + ") khác với tên Chủ két (" + cleanExpectedOwner + "). Mức độ chứng minh sự qua đời của Chủ két chỉ đạt " + deathProofPct + "%.");
        }

        int score = 95;
        if (cccdMismatchWarning != null) {
            score = 42;
        } else if (simulateBlurry) {
            score = 62;
        } else if (simulateRelatedDoc && matched) {
            score = 76;
        } else if (!matched) {
            score = 35;
        }

        String summaryNote = buildSummaryNote(category, docType, certNo, deceasedName, deathDateStr, authority, matched, cleanExpectedOwner, deathProofPct, imgQuality);

        return DeathCertificateOcrResponseDto.builder()
                .documentCategory(category)
                .documentType(docType)
                .detectedHeaderTitle(detectedTitle)
                .titleAssessment(titleAssess)
                .imageQualityScore(imgQuality)
                .imageClarityLabel(imgClarity)
                .deathProofRelevancePercent(deathProofPct)
                .deathProofExplanation(deathProofExp)
                .executorSuggestion(execSuggest)
                .verifierSuggestion(verifSuggest)
                .certificateNumber(certNo)
                .bookNumber(bookNo)
                .deceasedFullName(deceasedName)
                .dateOfBirth(dobStr)
                .gender(genderStr)
                .idNumber(idNumberStr)
                .dateOfDeath(deathDateStr)
                .placeOfDeath(placeStr)
                .causeOfDeath("Suy tuần hoàn hô hấp cấp")
                .issuingAuthority(authority)
                .issueDate(issueDateStr)
                .signerName(simulateRelatedDoc ? "BS.CKII Phạm Văn Hùng (Giám đốc BV)" : "Lê Quốc Tuấn (Phó Chủ tịch UBND)")
                .hasOfficialSeal(true)
                .ownerNameMatched(matched)
                .expectedOwnerName(cleanExpectedOwner)
                .confidenceScore(score)
                .suggestedRejectionCode(suggestedRejection)
                .engineSource(ENGINE_DEMO)
                .summaryNote(summaryNote)
                .warnings(warnings)
                .build();
    }

    private static String buildDefaultDeathProofExplanation(String category, boolean matched, String deceasedName, String ownerName) {
        if (deceasedName == null) {
            return "Chưa đọc được họ tên người mất trên tài liệu nên chưa thể xác nhận mức độ chứng minh sự qua đời của Chủ két (" + ownerName + ").";
        }
        if (!matched) {
            return "Tài liệu có ghi nhận thông tin tử vong nhưng họ tên chủ thể KHÔNG trùng khớp với Chủ két ("
                    + ownerName + "), do đó chưa chứng minh được sự kiện qua đời của Chủ két.";
        }
        if (CAT_RELATED_DOC.equals(category)) {
            return "Nội dung văn bản y tế xác nhận trực tiếp thời điểm và nguyên nhân tử vong của Chủ két ("
                    + ownerName + "), tuy nhiên cần đối chiếu thêm Trích lục khai tử hộ tịch.";
        }
        if (CAT_UNRELATED_DOC.equals(category)) {
            return "Nội dung văn bản không chứa thông tin pháp lý hay y tế chứng minh sự kiện qua đời.";
        }
        return "Văn bản hộ tịch xác nhận trực tiếp sự kiện qua đời của Chủ két (" + ownerName + ").";
    }

    private static String buildDefaultExecutorSuggestion(String category, boolean matched, Integer imgQuality, String docType) {
        if (imgQuality != null && imgQuality < 60) {
            return "Hình ảnh tài liệu hiện hơi mờ (" + imgQuality + "%). Bạn vẫn có thể gửi hồ sơ nếu muốn, nhưng AI gợi ý nên chụp hoặc quét lại bản rõ nét hơn để tránh bị yêu cầu nộp lại.";
        }
        if (!matched) {
            return "Họ tên trên tài liệu chưa khớp với Chủ két (hoặc không đọc được). Gợi ý bạn kiểm tra lại tệp tài liệu trước khi bấm Gửi xác minh.";
        }
        if (CAT_RELATED_DOC.equals(category)) {
            return "Tệp đang chọn là \"" + (docType != null ? docType : "Tài liệu liên quan")
                    + "\". Bạn vẫn có thể nộp kèm giải trình trong ô ghi chú, hoặc thay bằng Bản sao Trích lục khai tử do UBND cấp để hồ sơ được duyệt nhanh nhất.";
        }
        return "Tài liệu rõ nét, tiêu đề và nội dung khớp với Chủ két. Bạn có thể tự tin gửi hồ sơ xác minh.";
    }

    private static String buildDefaultVerifierSuggestion(String category, boolean matched, Integer imgQuality, String docType, String ownerName) {
        if (imgQuality != null && imgQuality < 60) {
            return "Gợi ý cho Verifier: Độ nét ảnh thấp (" + imgQuality + "%). Đề xuất mở Xem tài liệu để kiểm tra trực tiếp bằng mắt thường; nếu không đọc được số trích lục/dấu đỏ, có thể cân nhắc lý do \"Tài liệu bị mờ hoặc không đọc được\".";
        }
        if (!matched) {
            return "Gợi ý cho Verifier: Họ tên trong tài liệu không khớp với tên Chủ két (" + ownerName + ") hoặc không đọc được. Đề xuất đối chiếu kỹ bản gốc; nếu xác nhận lệch nhân thân, có thể tham khảo lý do \"Thông tin không khớp với chủ tài khoản\".";
        }
        if (CAT_RELATED_DOC.equals(category)) {
            return "Gợi ý cho Verifier: Văn bản là \"" + (docType != null ? docType : "Tài liệu liên quan")
                    + "\" — khớp tên Chủ két nhưng chưa phải mẫu Trích lục khai tử UBND. Tùy quy chế thẩm định, bạn có thể nhắn tin yêu cầu Executor bổ sung Trích lục khai tử hoặc xem xét theo thẩm quyền.";
        }
        return "Gợi ý cho Verifier: Tiêu đề và họ tên khớp với Chủ két. Đề xuất đối chiếu trực tiếp số hiệu văn bản và con dấu trên bản gốc trước khi phê duyệt.";
    }

    private static int calculateWeightedScore(String category, boolean matched, boolean hasSeal, JsonNode extracted) {
        int score = 0;
        if (CAT_VALID_DEATH_CERT.equals(category)) {
            score += 40;
        } else if (CAT_RELATED_DOC.equals(category)) {
            score += 18;
        }
        if (matched) {
            score += 35;
        }
        int fieldsPresent = 0;
        for (String f : List.of("certificateNumber", "deceasedFullName", "dateOfDeath", "issuingAuthority", "signerName")) {
            if (textOrNull(extracted, f) != null) {
                fieldsPresent++;
            }
        }
        score += (fieldsPresent * 3);
        if (hasSeal) {
            score += 8;
        }
        return Math.min(99, Math.max(5, score));
    }

    private static String buildSummaryNote(
            String category,
            String docType,
            String certNo,
            String deceasedName,
            String deathDate,
            String authority,
            boolean matched,
            String expectedOwnerName,
            Integer deathProofPct,
            Integer imgQuality
    ) {
        if (deceasedName == null && certNo == null) {
            return null;
        }
        String safeDocType = docType != null ? docType : "Văn bản";
        String safeCertNo = certNo != null ? certNo : "Không đọc được";
        String safeAuthority = authority != null ? authority : "Không đọc được";
        String safeDeceased = deceasedName != null ? deceasedName : "Không đọc được";
        String safeDeathDate = deathDate != null ? deathDate : "Không đọc được";
        int safeImgQuality = imgQuality != null ? imgQuality : 0;
        int safeDeathProof = deathProofPct != null ? deathProofPct : 0;

        if (CAT_RELATED_DOC.equals(category)) {
            return String.format(
                    "[Gợi ý AI OCR] Loại văn bản: %s (Số: %s, cấp bởi: %s). Độ rõ nét: %d%%. Mức độ chứng minh sự qua đời: %d%%. Họ tên: %s (%s với Chủ két %s).",
                    safeDocType, safeCertNo, safeAuthority, safeImgQuality, safeDeathProof, safeDeceased,
                    matched ? "Khớp" : "Không khớp", expectedOwnerName
            );
        }
        String matchBadge = matched
                ? "Khớp họ tên với Chủ két (" + expectedOwnerName + ")"
                : "Không khớp với tên Chủ két (" + expectedOwnerName + ")";
        return String.format(
                "[Gợi ý AI OCR] %s (Số: %s, cơ quan cấp: %s). Độ rõ nét ảnh: %d%%. Mức độ chứng minh sự qua đời: %d%%. Họ tên người mất: %s (Thời điểm mất: %s) — %s.",
                safeDocType, safeCertNo, safeAuthority, safeImgQuality, safeDeathProof, safeDeceased, safeDeathDate, matchBadge
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String val = child.asString("").trim();
        if (val.isEmpty()
                || "null".equalsIgnoreCase(val)
                || "unknown".equalsIgnoreCase(val)
                || "n/a".equalsIgnoreCase(val)
                || "không rõ".equalsIgnoreCase(val)
                || "không đọc được".equalsIgnoreCase(val)) {
            return null;
        }
        return val;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull() || !child.isNumber()) {
            return null;
        }
        int val = child.asInt(-1);
        if (val < 0 || val > 100) {
            return null;
        }
        return val;
    }

    /**
     * Kiểm tra tính nhất quán nội tại ngay trên tờ Giấy chứng tử theo quy chuẩn CCCD 12 số Việt Nam:
     * - Chữ số thứ 4 (index 3): Mã thế kỷ & giới tính (0=Nam TK20, 1=Nữ TK20, 2=Nam TK21, 3=Nữ TK21)
     * - Chữ số thứ 5 & 6 (index 4..5): 2 chữ số cuối của Năm sinh (VD: 079065... -> Sinh năm 1965).
     */
    static String detectInternalCccdBirthYearMismatch(String dateOfBirth, String gender, String idNumber) {
        if (dateOfBirth == null || idNumber == null) {
            return null;
        }
        String digitsOnlyId = idNumber.replaceAll("\\D", "");
        if (digitsOnlyId.length() != 12) {
            return null;
        }
        Matcher yearMatcher = Pattern.compile("(19\\d{2}|20\\d{2})").matcher(dateOfBirth);
        if (!yearMatcher.find()) {
            return null;
        }
        int dobYear = Integer.parseInt(yearMatcher.group(1));
        int genderCenturyCode = Character.getNumericValue(digitsOnlyId.charAt(3));
        int twoDigitYear = Integer.parseInt(digitsOnlyId.substring(4, 6));

        int centuryBase;
        if (genderCenturyCode == 0 || genderCenturyCode == 1) {
            centuryBase = 1900;
        } else if (genderCenturyCode == 2 || genderCenturyCode == 3) {
            centuryBase = 2000;
        } else {
            return null;
        }

        int cccdBirthYear = centuryBase + twoDigitYear;
        if (dobYear != cccdBirthYear) {
            return String.format(
                    "PHÁT HIỆN MÂU THUẪN NỘI TẠI (NGHI VẤN LÀM GIẢ / SỬA CHI TIẾT NHỎ): Năm sinh ghi trên văn bản (%d) KHÔNG KHỚP với mã năm sinh ẩn trong số CCCD 12 số (%s -> mã thế kỷ/giới tính '%d' + mã năm '%02d' = sinh năm %d).",
                    dobYear, digitsOnlyId, genderCenturyCode, twoDigitYear, cccdBirthYear
            );
        }
        return null;
    }

    /**
     * So khớp chính xác toàn bộ họ tên sau khi chuẩn hóa tiếng Việt (không dùng contains
     * để tránh trường hợp tên ngắn "Văn An" khớp nhầm với "Trần Văn An").
     */
    static boolean isNameMatching(String extractedName, String expectedOwnerName) {
        if (extractedName == null || expectedOwnerName == null) {
            return false;
        }
        String normA = normalizeVietnamese(extractedName);
        String normB = normalizeVietnamese(expectedOwnerName);
        if (normA.isEmpty() || normB.isEmpty()) {
            return false;
        }
        return normA.equals(normB);
    }

    private static String normalizeVietnamese(String input) {
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    record CachedOcrEntry(DeathCertificateOcrResponseDto response, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
