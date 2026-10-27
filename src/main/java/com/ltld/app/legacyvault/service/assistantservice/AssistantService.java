package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatRequest;
import com.ltld.app.legacyvault.dto.assistantdto.ChatResponse;

import java.util.List;
import java.util.UUID;

public interface AssistantService {
    ChatResponse chat(UUID userId, List<String> roles, ChatRequest request);
}

