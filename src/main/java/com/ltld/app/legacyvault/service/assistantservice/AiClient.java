package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;

import java.util.List;

public interface AiClient {
    String chat(String systemPrompt, List<ChatMessage> messages);
}
