package com.ltld.app.legacyvault.dto.assistantdto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @NotEmpty @Size(max = 10) List<@Valid ChatMessage> messages
) {
}
