package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.AssetDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultPreviewResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BeneficiaryController.class)
@Import(SecurityConfig.class)
class BeneficiaryControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BeneficiaryService beneficiaryService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{id}/assets", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_ownerOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{id}/assets", UUID.randomUUID())
                        .with(token("ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verify(beneficiaryService, never()).getInheritedAssets(any(), any());
    }

    @Test
    void list_beneficiary_returns200() throws Exception {
        when(beneficiaryService.getInheritedAssets(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/beneficiaries/vaults/{id}/assets", UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isOk());
    }

    @Test
    void detail_beneficiaryWithoutAssetDownload_returns403() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/assets/{a}", UUID.randomUUID(), UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
        verify(beneficiaryService, never()).getInheritedAssetDetail(any(), any(), any());
    }

    @Test
    void detail_beneficiaryWithAssetDownload_returns200() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/assets/{a}", UUID.randomUUID(), UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY", "ASSET_DOWNLOAD")))
                .andExpect(status().isOk());
    }

    @Test
    void download_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/assets/{a}/download",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void download_ownerOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/assets/{a}/download",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(token("ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verify(beneficiaryService, never()).downloadInheritedAsset(any(), any(), any());
    }

    @Test
    void download_beneficiaryWithoutAssetDownload_returns403() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/assets/{a}/download",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
        verify(beneficiaryService, never()).downloadInheritedAsset(any(), any(), any());
    }

    @Test
    void download_beneficiaryWithAssetDownload_returns200() throws Exception {
        when(beneficiaryService.downloadInheritedAsset(any(), any(), any())).thenReturn(
                AssetDownloadResponse.builder().fileName("a.txt").content("data".getBytes()).build());

        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/assets/{a}/download",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY", "ASSET_DOWNLOAD")))
                .andExpect(status().isOk());
    }

    @Test
    void close_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/beneficiaries/vaults/{v}/close", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void close_ownerOnly_returns403() throws Exception {
        mockMvc.perform(post("/api/beneficiaries/vaults/{v}/close", UUID.randomUUID())
                        .with(token("ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isForbidden());
        verify(beneficiaryService, never()).closeVault(any(), any(), any());
    }

    @Test
    void close_beneficiary_returns200() throws Exception {
        when(beneficiaryService.closeVault(any(), any(), any())).thenReturn(
                CloseVaultResponse.builder().build());

        mockMvc.perform(post("/api/beneficiaries/vaults/{v}/close", UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void closePreview_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/close-preview", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void closePreview_ownerOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/close-preview", UUID.randomUUID())
                        .with(token("ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verify(beneficiaryService, never()).previewClose(any(), any());
    }

    @Test
    void closePreview_beneficiary_returns200() throws Exception {
        when(beneficiaryService.previewClose(any(), any())).thenReturn(
                CloseVaultPreviewResponse.builder().build());

        mockMvc.perform(get("/api/beneficiaries/vaults/{v}/close-preview", UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isOk());
    }
}