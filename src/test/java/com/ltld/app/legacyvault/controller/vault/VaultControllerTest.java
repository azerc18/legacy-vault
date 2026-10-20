package com.ltld.app.legacyvault.controller.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;
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
    @DisplayName("createVault: Tên két trống -> 400 Bad Request")
    void createVault_blankName_returns400() throws Exception {
        UUID ownerId = UUID.randomUUID();
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("   "); // blank

        mockMvc.perform(post("/api/vaults")
                        .principal(() -> ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vaultService, never()).createVault(any(), any());
    }

    @Test
    @DisplayName("createVault (m2): Mô tả dài hơn 500 ký tự -> 400 Bad Request")
    void createVault_descriptionTooLong_returns400() throws Exception {
        UUID ownerId = UUID.randomUUID();
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("Vault 1");
        request.setDescription("a".repeat(501));

        mockMvc.perform(post("/api/vaults")
                        .principal(() -> ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vaultService, never()).createVault(any(), any());
    }

    @Test
    @DisplayName("createVault: Asset con thiếu tên/loại -> 400 Bad Request")
    void createVault_invalidNestedAsset_returns400() throws Exception {
        UUID ownerId = UUID.randomUUID();
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("Vault 1");
        CreateVaultRequest.DigitalAssetDto asset = new CreateVaultRequest.DigitalAssetDto();
        // thiếu assetType và assetName
        asset.setSecret("secret123");
        request.setAssets(List.of(asset));

        mockMvc.perform(post("/api/vaults")
                        .principal(() -> ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vaultService, never()).createVault(any(), any());
    }

    @Test
    @DisplayName("createVault (m1): Asset con thiếu secret -> 400 Bad Request")
    void createVault_nestedAssetMissingSecret_returns400() throws Exception {
        UUID ownerId = UUID.randomUUID();
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("Vault 1");
        CreateVaultRequest.DigitalAssetDto asset = new CreateVaultRequest.DigitalAssetDto();
        asset.setAssetType(AssetType.BANK_ACCOUNT);
        asset.setAssetName("MB Bank");
        asset.setSecret(""); // trống secret
        request.setAssets(List.of(asset));

        mockMvc.perform(post("/api/vaults")
                        .principal(() -> ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vaultService, never()).createVault(any(), any());
    }

    @Test
    @DisplayName("createVault (n4): Quá 50 tài sản số -> 400 Bad Request")
    void createVault_tooManyAssets_returns400() throws Exception {
        UUID ownerId = UUID.randomUUID();
        CreateVaultRequest request = new CreateVaultRequest();
        request.setName("Vault 1");
        List<CreateVaultRequest.DigitalAssetDto> assets = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> {
                    CreateVaultRequest.DigitalAssetDto dto = new CreateVaultRequest.DigitalAssetDto();
                    dto.setAssetType(AssetType.BANK_ACCOUNT);
                    dto.setAssetName("Bank " + i);
                    dto.setSecret("secret " + i);
                    return dto;
                }).toList();
        request.setAssets(assets);

        mockMvc.perform(post("/api/vaults")
                        .principal(() -> ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(vaultService, never()).createVault(any(), any());
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
