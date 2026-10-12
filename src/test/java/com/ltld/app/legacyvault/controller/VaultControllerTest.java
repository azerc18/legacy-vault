package com.ltld.app.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void assignBeneficiary_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        AssignBeneficiaryResponse response = AssignBeneficiaryResponse.builder()
                .vaultId(vaultId)
                .beneficiaryEmail("ben@example.com")
                .pendingRegistration(true)
                .build();
        when(vaultService.assignBeneficiary(eq(ownerId), eq(vaultId), any(AssignBeneficiaryRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/vaults/{vaultId}/beneficiary", vaultId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ben@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.beneficiaryEmail").value("ben@example.com"))
                .andExpect(jsonPath("$.data.pendingRegistration").value(true));
    }

    @Test
    void assignBeneficiary_InvalidEmail_ReturnsBadRequest() throws Exception {
        Principal mockPrincipal = () -> UUID.randomUUID().toString();

        mockMvc.perform(put("/api/vaults/{vaultId}/beneficiary", UUID.randomUUID())
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.email").exists());

        verify(vaultService, never()).assignBeneficiary(any(), any(), any());
    }

    @Test
    void assignBeneficiary_ServiceConflict_Returns409() throws Exception {
        Principal mockPrincipal = () -> UUID.randomUUID().toString();
        when(vaultService.assignBeneficiary(any(), any(), any()))
                .thenThrow(new VaultException("Vault đã có người thụ hưởng", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/vaults/{vaultId}/beneficiary", UUID.randomUUID())
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ben@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Vault đã có người thụ hưởng"));
    }
}
