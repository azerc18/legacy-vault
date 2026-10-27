package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiClient implements AiClient {

    @Override
    public String chat(String systemPrompt, List<ChatMessage> messages) {
        String q = messages.get(messages.size() - 1).content().toLowerCase();
        if (q.contains("vault")) return "To create a vault: 1) Go to My vaults 2) Click New vault 3) Fill in the name and save.";
        if (q.contains("dms") || q.contains("switch"))
            return "The Dead Man's Switch activates when you miss check-ins. Check in from the DMS page to reset it.";
        return "I'm a demo assistant. Ask me about vaults, executors or the Dead Man's Switch.";
    }
}
