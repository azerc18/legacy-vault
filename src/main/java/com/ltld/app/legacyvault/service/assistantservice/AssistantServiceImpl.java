package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.service.aifallbackservice.FallbackAiClient;
import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import com.ltld.app.legacyvault.dto.assistantdto.ChatRequest;
import com.ltld.app.legacyvault.dto.assistantdto.ChatResponse;
import com.ltld.app.legacyvault.enums.AiFeature;
import com.ltld.app.legacyvault.exception.AssistantException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.aisettingsservice.AiFeatureConfig;
import com.ltld.app.legacyvault.service.aisettingsservice.AiSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final int MAX_MESSAGES_PER_MINUTE = 20;

    private static final String SYSTEM_TEMPLATE = """
            You are Vaulty, the in-app guide of LegacyVault.
            Only answer questions about how to use LegacyVault, based on the GUIDE below.
            The current user's roles: %s. Prefer steps for these roles.
            If the question is off-topic or not covered, say you don't know and suggest contacting an admin.
            Never ask for or repeat passwords, OTP codes or asset secrets.
            Reply in the user's language, short, using numbered steps when useful.
            Only describe buttons, pages and steps that appear in the GUIDE. Never invent new ones.
            Keep answers under 120 words. Do not use headings or tables; use **bold** only for button and page names.
            GUIDE:
            %s
            """;

    private final AiClient aiClient;
    private final String guide;
    private final Map<UUID, Deque<Instant>> hits = new ConcurrentHashMap<>();

    // Nhận Resource qua constructor để unit test truyền ByteArrayResource, không cần Spring context
    public AssistantServiceImpl(AiClient aiClient,
                                @Value("classpath:assistant/guide.md") Resource guideResource) throws IOException {
        this.aiClient = aiClient;
        this.guide = guideResource.getContentAsString(StandardCharsets.UTF_8);
    }

    // Trang Cấu hình AI: Admin tắt Vaulty / chọn model. Không có (unit test) thì luôn bật, model mặc định.
    private AiSettingsService aiSettings;

    @Autowired(required = false)
    void setAiSettings(AiSettingsService aiSettings) {
        this.aiSettings = aiSettings;
    }

    // AI dự phòng (Groq / DeepSeek) khi Gemini hỏng. Không có (unit test) thì lỗi Gemini trả 503 như trước.
    private FallbackAiClient fallback;

    @Autowired(required = false)
    void setFallback(FallbackAiClient fallback) {
        this.fallback = fallback;
    }

    @Override
    public ChatResponse chat(UUID userId, List<String> roles, ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        if (!"user".equals(messages.get(messages.size() - 1).role())) {
            throw new AssistantException("The last message must be from the user.", HttpStatus.BAD_REQUEST);
        }
        AiFeatureConfig config = aiSettings == null ? null : aiSettings.forFeature(AiFeature.ASSISTANT);
        if (config != null && !config.enabled()) {
            throw new AssistantException(ErrorCode.AI_FEATURE_DISABLED, "Vaulty is turned off by the administrator.");
        }
        checkRate(userId);

        String systemPrompt = SYSTEM_TEMPLATE.formatted(
                roles.isEmpty() ? "none" : String.join(", ", roles), guide);

        String reply;
        try {
            reply = aiClient.chat(systemPrompt, messages, config);
        } catch (RuntimeException e) {
            // Không log nội dung tin nhắn, chỉ log lỗi
            log.warn("Assistant call failed for user {}: {}", userId, e.getMessage());
            reply = fallbackReply(userId, systemPrompt, messages, config);
        }
        return new ChatResponse(reply == null || reply.isBlank()
                ? "Sorry, I couldn't answer that."
                : reply.strip());
    }

    /**
     * Gemini (cả model chính lẫn dự phòng) hỏng: trả lời bằng nhà cung cấp văn bản Admin chọn (Groq / DeepSeek).
     * Không có dự phòng dùng được, hoặc dự phòng cũng lỗi, thì 503 như trước.
     */
    private String fallbackReply(UUID userId, String systemPrompt, List<ChatMessage> messages, AiFeatureConfig config) {
        if (fallback != null && !(aiClient instanceof MockAiClient) && fallback.textAvailable(config)) {
            try {
                String reply = fallback.chat(config, systemPrompt, messages, 0.4, 1024);
                log.info("Assistant answered by fallback {} for user {}", fallback.describeText(config), userId);
                return reply;
            } catch (RuntimeException fe) {
                log.warn("Assistant fallback {} failed for user {}: {}", fallback.describeText(config), userId,
                        fe.getClass().getSimpleName());
            }
        }
        throw new AssistantException("Assistant is unavailable right now. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    // Tối đa 20 tin / 60 giây / user, lưu trong bộ nhớ (reset khi restart, đủ cho demo)
    private void checkRate(UUID userId) {
        Deque<Instant> q = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant cutoff = Instant.now().minusSeconds(60);
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= MAX_MESSAGES_PER_MINUTE) {
                throw new AssistantException("Too many messages. Please wait a minute.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            q.addLast(Instant.now());
        }
    }
}