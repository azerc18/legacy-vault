package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.DmsCheckinLinkController;
import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinServiceImpl;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2: token check-in một chạm và access token không dùng thay cho nhau được.
 * SecurityConfig, JwtConfig (JwtDecoder/JwtEncoder thật) và DmsOneClickCheckinServiceImpl thật; DmsConfigService mock.
 * Mọi request tới /api/dms/checkin-link KHÔNG kèm header Authorization, như khi Owner bấm từ email (C1).
 */
@WebMvcTest(
        controllers = {VaultController.class, DmsCheckinLinkController.class},
        properties = "security.jwt.secret=dms-checkin-security-test-secret-at-least-32-bytes!!")
@Import({SecurityConfig.class, JwtConfig.class, DmsOneClickCheckinServiceImpl.class})
class DmsCheckinTokenSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private DmsOneClickCheckinService dmsOneClickCheckinService;

    @MockitoBean private VaultService vaultService;
    @MockitoBean private DmsConfigRepository dmsConfigRepository;
    @MockitoBean private DmsConfigService dmsConfigService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID configId = UUID.randomUUID();
    private final LocalDateTime lastCheckinAt = LocalDateTime.of(2026, 11, 1, 8, 0, 0);

    @BeforeEach
    void setUp() {
        DmsConfig config = DmsConfig.builder()
                .id(configId)
                .owner(User.builder().id(ownerId).email("owner@example.test").build())
                .lastCheckinAt(lastCheckinAt)
                .build();
        when(dmsConfigRepository.findById(configId)).thenReturn(Optional.of(config));
    }

    // ---------- (a) token check-in KHÔNG dùng được như access token ----------

    @Test
    void checkinToken_asBearer_onOwnerVaults_returns401() throws Exception {
        mockMvc.perform(get("/api/owner/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + checkinToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkinToken_asBearer_onVaults_returns401() throws Exception {
        mockMvc.perform(get("/api/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + checkinToken()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(vaultService);
    }

    // Đối chứng: cùng request với access token thật thì KHÔNG bị 401, nên 401 ở trên là do token bị từ chối.
    @Test
    void accessToken_asBearer_isAccepted_control() throws Exception {
        String accessToken = accessToken();

        mockMvc.perform(get("/api/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/owner/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    // ---------- (b) access token KHÔNG dùng được làm token check-in ----------

    @Test
    void accessToken_asCheckinLinkToken_withoutAuthorization_returnsCheckinTokenInvalid() throws Exception {
        mockMvc.perform(post("/api/dms/checkin-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + accessToken() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHECKIN_TOKEN_INVALID"))
                .andExpect(jsonPath("$.data.reason").value("TOKEN_INVALID"));

        verifyNoInteractions(dmsConfigService);
    }

    // ---------- (c) C1: không header + token check-in hợp lệ -> qua security và check-in ----------

    @Test
    void checkinToken_withoutAuthorization_isNotUnauthorizedAndChecksIn() throws Exception {
        when(dmsConfigService.checkInFromEmailLink(eq(ownerId), anyString(), eq(1793520000L)))
                .thenReturn(DmsConfigResponse.builder().checkinDueAt(lastCheckinAt.plusDays(30)).build());

        mockMvc.perform(post("/api/dms/checkin-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + checkinToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkinDueAt").value("2026-12-01T08:00:00"));

        // cyc = lastCheckinAt 2026-11-01T08:00 (UTC epoch giây)
        verify(dmsConfigService).checkInFromEmailLink(eq(ownerId), anyString(), eq(1793520000L));
    }

    private String checkinToken() {
        return dmsOneClickCheckinService.generateCheckinTokenForConfig(configId);
    }

    // Access token giống TokenServiceImpl.generateAccessToken.
    private String accessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(ownerId.toString())
                .claim("roles", List.of("OWNER"))
                .claim("authorities", List.of("VAULT_READ"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
