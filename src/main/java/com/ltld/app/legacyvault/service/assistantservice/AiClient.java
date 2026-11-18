package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import com.ltld.app.legacyvault.service.aisettingsservice.AiFeatureConfig;

import java.util.List;

public interface AiClient {
    String chat(String systemPrompt, List<ChatMessage> messages);

    /**
     * Gọi với model Admin đã chọn ở trang Cấu hình AI. Client không phân biệt model (mock) giữ hành vi cũ.
     * {@code config} null = dùng model mặc định của client.
     */
    default String chat(String systemPrompt, List<ChatMessage> messages, AiFeatureConfig config) {
        return chat(systemPrompt, messages);
    }
}
