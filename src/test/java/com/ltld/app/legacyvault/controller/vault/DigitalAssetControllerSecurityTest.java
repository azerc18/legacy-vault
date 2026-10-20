package com.ltld.app.legacyvault.controller.vault;

import com.ltld.app.legacyvault.controller.DigitalAssetController;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.vaultservice.DigitalAssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security tests for {@link DigitalAssetController} (m2 / FR-27).
 *
 * <pre>
 * S1: Không token → 401 cho cả 5 endpoint, service không được gọi
 * S2: Sai authority → 403 (GET cần VAULT_READ; POST/PUT/DELETE cần VAULT_CREATE)
 * S3: GET /{assetId} với Owner hợp lệ → 200 kèm header Cache-Control: no-store
 * S4: POST validation error (thiếu assetType, thiếu assetName, secret > 5000 ký tự) → 400
 * Admin: Admin có VAULT_READ qua được @PreAuthorize nhưng service chặn sai chủ → 404
 * </pre>
 */
@WebMvcTest(DigitalAssetController.class)
@Import(SecurityConfig.class)
@DisplayName("DigitalAssetControllerSecurityTest – m2 / FR-27")
class DigitalAssetControllerSecurityTest {

    private static final String BASE_URL = "/api/vaults/{vaultId}/assets";
    private static final String DETAIL_URL = "/api/vaults/{vaultId}/assets/{assetId}";
    private static final UUID VAULT_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    private static final String VALID_BODY = """
            {
              "assetType": "BANK_ACCOUNT",
              "assetName": "MB Bank",
              "secret": "my-secret-password",
              "notes": "some notes"
            }
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DigitalAssetService digitalAssetService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    // =========================================================================
    // S1: Unauthenticated (Không token) → 401, service không được gọi
    // =========================================================================
    @Nested
    @DisplayName("S1: Không token → 401 cho cả 5 endpoint")
    class S1_Unauthenticated {

        @Test
        @DisplayName("GET listAssets không token → 401")
        void listAssets_noToken_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL, VAULT_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("GET getAsset không token → 401")
        void getAsset_noToken_returns401() throws Exception {
            mockMvc.perform(get(DETAIL_URL, VAULT_ID, ASSET_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("POST addAsset không token → 401")
        void addAsset_noToken_returns401() throws Exception {
            mockMvc.perform(post(BASE_URL, VAULT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("PUT updateAsset không token → 401")
        void updateAsset_noToken_returns401() throws Exception {
            mockMvc.perform(put(DETAIL_URL, VAULT_ID, ASSET_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("DELETE deleteAsset không token → 401")
        void deleteAsset_noToken_returns401() throws Exception {
            mockMvc.perform(delete(DETAIL_URL, VAULT_ID, ASSET_ID))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(digitalAssetService);
        }
    }

    // =========================================================================
    // S2: Sai authority → 403, service không được gọi
    // =========================================================================
    @Nested
    @DisplayName("S2: Thiếu authority yêu cầu → 403")
    class S2_Authorization {

        @Test
        @DisplayName("GET listAssets không có VAULT_READ → 403")
        void listAssets_missingVaultRead_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL, VAULT_ID)
                            .with(token("OTHER_AUTH")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("GET getAsset không có VAULT_READ → 403")
        void getAsset_missingVaultRead_returns403() throws Exception {
            mockMvc.perform(get(DETAIL_URL, VAULT_ID, ASSET_ID)
                            .with(token("OTHER_AUTH")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("POST addAsset chỉ có VAULT_READ (thiếu VAULT_CREATE) → 403")
        void addAsset_missingVaultCreate_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL, VAULT_ID)
                            .with(token("VAULT_READ"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("PUT updateAsset chỉ có VAULT_READ (thiếu VAULT_CREATE) → 403")
        void updateAsset_missingVaultCreate_returns403() throws Exception {
            mockMvc.perform(put(DETAIL_URL, VAULT_ID, ASSET_ID)
                            .with(token("VAULT_READ"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("DELETE deleteAsset chỉ có VAULT_READ (thiếu VAULT_CREATE) → 403")
        void deleteAsset_missingVaultCreate_returns403() throws Exception {
            mockMvc.perform(delete(DETAIL_URL, VAULT_ID, ASSET_ID)
                            .with(token("VAULT_READ")))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(digitalAssetService);
        }
    }

    // =========================================================================
    // S3: GET /{assetId} với Owner hợp lệ → 200 kèm Cache-Control: no-store
    // =========================================================================
    @Nested
    @DisplayName("S3: Header bảo mật khi xem secret chi tiết")
    class S3_SecretDetailSecurity {

        @Test
        @DisplayName("GET getAsset hợp lệ → 200 và có Cache-Control chứa no-store")
        void getAsset_validOwner_returns200WithNoStoreCacheControl() throws Exception {
            DigitalAssetDetailResponse detail = DigitalAssetDetailResponse.builder()
                    .id(ASSET_ID)
                    .assetType(AssetType.BANK_ACCOUNT)
                    .assetName("MB Bank")
                    .secret("plain-secret-password")
                    .notes("notes")
                    .status(AssetStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(digitalAssetService.getAsset(any(), eq(VAULT_ID), eq(ASSET_ID))).thenReturn(detail);

            mockMvc.perform(get(DETAIL_URL, VAULT_ID, ASSET_ID)
                            .with(token("VAULT_READ")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.data.secret").value("plain-secret-password"));

            verify(digitalAssetService).getAsset(any(), eq(VAULT_ID), eq(ASSET_ID));
        }
    }

    // =========================================================================
    // S4: Validation errors → 400, service không được gọi
    // =========================================================================
    @Nested
    @DisplayName("S4: Validate request body khi thêm tài sản số")
    class S4_Validation {

        @Test
        @DisplayName("POST thiếu assetType → 400")
        void addAsset_missingAssetType_returns400() throws Exception {
            String bodyWithoutType = """
                    {
                      "assetName": "MB Bank",
                      "secret": "my-secret-password"
                    }
                    """;

            mockMvc.perform(post(BASE_URL, VAULT_ID)
                            .with(token("VAULT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithoutType))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("POST thiếu assetName → 400")
        void addAsset_missingAssetName_returns400() throws Exception {
            String bodyWithoutName = """
                    {
                      "assetType": "BANK_ACCOUNT",
                      "secret": "my-secret-password"
                    }
                    """;

            mockMvc.perform(post(BASE_URL, VAULT_ID)
                            .with(token("VAULT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithoutName))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(digitalAssetService);
        }

        @Test
        @DisplayName("POST secret dài hơn 5000 ký tự → 400")
        void addAsset_secretTooLong_returns400() throws Exception {
            String longSecret = "s".repeat(5001);
            String bodyWithLongSecret = """
                    {
                      "assetType": "BANK_ACCOUNT",
                      "assetName": "MB Bank",
                      "secret": "%s"
                    }
                    """.formatted(longSecret);

            mockMvc.perform(post(BASE_URL, VAULT_ID)
                            .with(token("VAULT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithLongSecret))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(digitalAssetService);
        }
    }

    // =========================================================================
    // Admin case: Admin có VAULT_READ qua được @PreAuthorize nhưng service chặn sai chủ → 404
    // =========================================================================
    @Nested
    @DisplayName("Admin test: Admin có VAULT_READ nhưng không phải owner → service trả 404")
    class AdminAccess {

        @Test
        @DisplayName("Admin gọi GET asset qua @PreAuthorize nhưng service trả 404")
        void getAsset_adminHasVaultRead_serviceReturns404() throws Exception {
            when(digitalAssetService.getAsset(any(), eq(VAULT_ID), eq(ASSET_ID)))
                    .thenThrow(new VaultException("Vault not found", HttpStatus.NOT_FOUND));

            mockMvc.perform(get(DETAIL_URL, VAULT_ID, ASSET_ID)
                            .with(token("ROLE_ADMIN", "VAULT_READ")))
                    .andExpect(status().isNotFound());

            verify(digitalAssetService).getAsset(any(), eq(VAULT_ID), eq(ASSET_ID));
        }
    }
}
