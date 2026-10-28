package com.ltld.app.legacyvault.controller.vault;

import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.dto.vaultdto.AdminVaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VaultController.class)
@Import(SecurityConfig.class)
class VaultControllerSecurityTest {

    private static final String BODY = "{\"email\":\"ben@example.com\"}";
    private static final String UPDATE_BODY = "{\"name\":\"Tên mới\",\"description\":\"Mô tả mới\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultService vaultService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    // --- PUT /api/vaults/{vaultId}/beneficiary ---
    @Test
    void assignBeneficiary_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignBeneficiary_beneficiaryOnly_returns403() throws Exception {
        mockMvc.perform(put("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verify(vaultService, never()).assignBeneficiary(any(), any(), any());
    }

    @Test
    void assignBeneficiary_owner_returns200() throws Exception {
        when(vaultService.assignBeneficiary(any(), any(), any()))
                .thenReturn(AssignBeneficiaryResponse.builder().build());

        mockMvc.perform(put("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .with(token("ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    // --- DELETE /api/vaults/{vaultId}/beneficiary ---
    @Test
    void removeBeneficiary_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/vaults/{v}/beneficiary", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeBeneficiary_owner_returns200() throws Exception {
        mockMvc.perform(delete("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .with(token("ROLE_OWNER")))
                .andExpect(status().isOk());
    }

    // --- GET /api/vaults ---
    @Test
    void getMyVaults_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/vaults"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyVaults_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/vaults")
                        .with(token("OTHER_AUTH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyVaults_withVaultRead_returns200() throws Exception {
        when(vaultService.getMyVaults(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/vaults")
                        .with(token("VAULT_READ")))
                .andExpect(status().isOk());
    }

    // --- GET /api/vaults/{vaultId} ---
    @Test
    void getVaultDetail_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/vaults/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getVaultDetail_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/vaults/{id}", UUID.randomUUID())
                        .with(token("OTHER_AUTH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getVaultDetail_withVaultRead_returns200() throws Exception {
        when(vaultService.getVaultDetail(any(), any()))
                .thenReturn(VaultDetailResponse.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(get("/api/vaults/{id}", UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isOk());
    }

    // --- PUT /api/vaults/{vaultId} ---
    @Test
    void updateVault_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/vaults/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateVault_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(put("/api/vaults/{id}", UUID.randomUUID())
                        .with(token("VAULT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateVault_withVaultCreate_returns200() throws Exception {
        when(vaultService.updateVault(any(), any(), any()))
                .thenReturn(VaultResponse.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(put("/api/vaults/{id}", UUID.randomUUID())
                        .with(token("VAULT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk());
    }

    // --- GET /api/vaults/admin/all ---
    @Test
    void getAllVaultsForAdmin_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/vaults/admin/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllVaultsForAdmin_userAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/vaults/admin/all")
                        .with(token("VAULT_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllVaultsForAdmin_withAdminManage_returns200() throws Exception {
        when(vaultService.getAllVaultsForAdmin(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/vaults/admin/all")
                        .with(token("ADMIN_MANAGE")))
                .andExpect(status().isOk());
    }

    // --- GET /api/vaults/admin/{vaultId} ---
    @Test
    void getAdminVaultDetail_userAuthority_returns403() throws Exception {
        mockMvc.perform(get("/api/vaults/admin/{id}", UUID.randomUUID())
                        .with(token("VAULT_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAdminVaultDetail_withAdminManage_returns200() throws Exception {
        when(vaultService.getAdminVaultDetail(any(), any()))
                .thenReturn(AdminVaultDetailResponse.builder().id(UUID.randomUUID()).build());

        mockMvc.perform(get("/api/vaults/admin/{id}", UUID.randomUUID())
                        .with(token("ADMIN_MANAGE")))
                .andExpect(status().isOk());
    }
}
