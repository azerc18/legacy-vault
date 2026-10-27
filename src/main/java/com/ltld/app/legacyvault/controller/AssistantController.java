package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.assistantdto.ChatRequest;
import com.ltld.app.legacyvault.dto.assistantdto.ChatResponse;
import com.ltld.app.legacyvault.service.assistantservice.AssistantService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final AssistantService assistantService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            Authentication auth, @Valid @RequestBody ChatRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(assistantService.chat(userId, roles, request)));
    }
}
