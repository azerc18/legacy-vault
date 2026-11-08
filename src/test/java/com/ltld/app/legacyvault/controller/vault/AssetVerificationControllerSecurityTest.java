package com.ltld.app.legacyvault.controller.vault;

import com.ltld.app.legacyvault.controller.AssetVerificationController;
import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests cho {@link AssetVerificationController} (C.5, PR 0).
 *
 * <pre>
 * S1: Không token → 401 cho cả 3 endpoint, service không được gọi
 * S2: Sai authority → 403 (GET cần VAULT_READ; POST start/confirm cần VAULT_CREATE)
 * S3: Đúng authority → 200; Owner khác (service ném 404) → 404
 * S4: Thiếu "method" trong body → 400
 * </pre>
 */
@WebMvcTest(AssetVerificationController.class)
@Import(SecurityConfig.class)
@DisplayName("AssetVerificationControllerSecurityTest – C.5 khung xác thực tài sản")
class AssetVerificationControllerSecurityTest {

    private static final String URL = "/api/vaults/{vaultId}/assets/{assetId}/verification";
    private static final String START_URL = URL + "/start";
    private static final String CONFIRM_URL = URL + "/confirm";
    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    private static final String START_BODY = """
            { "method": "OTP_EMAIL" }
            """;
    private static final String CONFIRM_BODY = """
            { "method": "OTP_EMAIL", "payload": { "code": "123456" } }
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AssetVerificationService assetVerificationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    private static AssetVerificationResponse selfDeclared() {
        return AssetVerificationResponse.builder()
                .assetId(ASSET_ID)
                .status(AssetVerificationStatus.SELF_DECLARED)
                .availableMethods(List.of())
                .build();
    }

    // ===== S1: không token → 401 =====

    @Test
    @DisplayName("S1: GET trạng thái không token → 401")
    void status_noToken_returns401() throws Exception {
        mockMvc.perform(get(URL, VAULT_ID, ASSET_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assetVerificationService);
    }

    @Test
    @DisplayName("S1: POST start không token → 401")
    void start_noToken_returns401() throws Exception {
        mockMvc.perform(post(START_URL, VAULT_ID, ASSET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assetVerificationService);
    }

    @Test
    @DisplayName("S1: POST confirm không token → 401")
    void confirm_noToken_returns401() throws Exception {
        mockMvc.perform(post(CONFIRM_URL, VAULT_ID, ASSET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assetVerificationService);
    }

    // ===== S2: sai authority → 403 =====

    @Test
    @DisplayName("S2: GET trạng thái thiếu VAULT_READ → 403")
    void status_wrongAuthority_returns403() throws Exception {
        mockMvc.perform(get(URL, VAULT_ID, ASSET_ID).with(token("OTHER_AUTH")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assetVerificationService);
    }

    @Test
    @DisplayName("S2: POST start chỉ có VAULT_READ (thiếu VAULT_CREATE) → 403")
    void start_missingVaultCreate_returns403() throws Exception {
        mockMvc.perform(post(START_URL, VAULT_ID, ASSET_ID)
                        .with(token("VAULT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assetVerificationService);
    }

    @Test
    @DisplayName("S2: POST confirm chỉ có VAULT_READ (thiếu VAULT_CREATE) → 403")
    void confirm_missingVaultCreate_returns403() throws Exception {
        mockMvc.perform(post(CONFIRM_URL, VAULT_ID, ASSET_ID)
                        .with(token("VAULT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assetVerificationService);
    }

    // ===== S3: đúng authority → 200; Owner khác → 404 =====

    @Test
    @DisplayName("S3: GET trạng thái đúng VAULT_READ → 200, status SELF_DECLARED")
    void status_validOwner_returns200() throws Exception {
        when(assetVerificationService.getStatus(any(), eq(VAULT_ID), eq(ASSET_ID)))
                .thenReturn(selfDeclared());

        mockMvc.perform(get(URL, VAULT_ID, ASSET_ID).with(token("VAULT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SELF_DECLARED"));

        verify(assetVerificationService).getStatus(any(), eq(VAULT_ID), eq(ASSET_ID));
    }

    @Test
    @DisplayName("S3: Owner khác (service ném 404) → 404")
    void status_otherOwner_returns404() throws Exception {
        when(assetVerificationService.getStatus(any(), eq(VAULT_ID), eq(ASSET_ID)))
                .thenThrow(new VaultException("Vault not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get(URL, VAULT_ID, ASSET_ID).with(token("VAULT_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("S3: POST start đúng VAULT_CREATE → 200 và gọi service")
    void start_validOwner_returns200() throws Exception {
        when(assetVerificationService.start(any(), eq(VAULT_ID), eq(ASSET_ID), any()))
                .thenReturn(selfDeclared());

        mockMvc.perform(post(START_URL, VAULT_ID, ASSET_ID)
                        .with(token("VAULT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_BODY))
                .andExpect(status().isOk());

        verify(assetVerificationService).start(any(), eq(VAULT_ID), eq(ASSET_ID), any());
    }

    // ===== S4: validation =====

    @Test
    @DisplayName("S4: POST start thiếu method → 400, service không được gọi")
    void start_missingMethod_returns400() throws Exception {
        mockMvc.perform(post(START_URL, VAULT_ID, ASSET_ID)
                        .with(token("VAULT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assetVerificationService);
    }

    @Test
    @DisplayName("S4: POST confirm thiếu method → 400, service không được gọi")
    void confirm_missingMethod_returns400() throws Exception {
        mockMvc.perform(post(CONFIRM_URL, VAULT_ID, ASSET_ID)
                        .with(token("VAULT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"payload\": { \"code\": \"123456\" } }"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(assetVerificationService);
    }
}
