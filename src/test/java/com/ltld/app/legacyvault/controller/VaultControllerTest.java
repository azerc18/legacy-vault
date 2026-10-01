package com.ltld.app.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VaultController.class)
public class VaultControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Dùng @MockitoBean theo đúng phiên bản Spring Boot mới nhất của dự án
    @MockitoBean private VaultService vaultService;

    @Test
    void createVault_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("My Vault");

        Vault mockVault = new Vault();
        mockVault.setName("My Vault");

        // Giả lập đối tượng đăng nhập
        Principal mockPrincipal = () -> ownerId.toString();

        when(vaultService.createVault(eq(ownerId), any(CreateVaultRequest.class))).thenReturn(mockVault);

        mockMvc.perform(post("/api/vaults")
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vault created successfully"));
    }

    @Test
    void deleteVault_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();

        Principal mockPrincipal = () -> ownerId.toString();

        mockMvc.perform(delete("/api/vaults/{vaultId}", vaultId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vault deleted successfully"));
    }
}
