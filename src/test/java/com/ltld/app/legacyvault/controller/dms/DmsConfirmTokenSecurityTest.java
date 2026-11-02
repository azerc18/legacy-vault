package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerServiceImpl;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * X2 (giống F2): token xác nhận chéo và access token không dùng thay cho nhau được.
 * SecurityConfig, JwtConfig (JwtDecoder/JwtEncoder thật) và DmsConfirmerServiceImpl thật; chỉ mock tầng dữ liệu.
 */
@WebMvcTest(
        controllers = {VaultController.class, DmsConfirmerController.class},
        properties = {
                "security.jwt.secret=dms-confirm-security-test-secret-at-least-32-bytes!!",
                "app.frontend.base-url=http://fe.test",
                "app.backend.base-url=http://api.test"
        })
@Import({SecurityConfig.class, JwtConfig.class, DmsConfirmerServiceImpl.class})
class DmsConfirmTokenSecurityTest {

    private static final String INVALID_REDIRECT =
            "http://fe.test/dms/confirm-done?status=error&errorCode=CONFIRMER_TOKEN_INVALID";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private DmsConfirmerService dmsConfirmerService;

    @MockitoBean private VaultService vaultService;
    @MockitoBean private DmsConfigRepository dmsConfigRepository;
    @MockitoBean private DmsConfirmerRepository dmsConfirmerRepository;
    @MockitoBean private DmsActivationProcessor dmsActivationProcessor;
    @MockitoBean private EmailSender emailSender;
    @MockitoBean private AuditLogService auditLogService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID confirmerId = UUID.randomUUID();

    // ---------- (a) token xác nhận KHÔNG dùng được như access token ----------

    @Test
    void confirmToken_asBearer_onVaults_returns401() throws Exception {
        String confirmToken = dmsConfirmerService.generateConfirmationToken(confirmerId, DmsConfirmerDecision.ALIVE);

        mockMvc.perform(get("/api/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + confirmToken))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(vaultService);
    }

    // Đối chứng: cùng request với access token thật thì KHÔNG bị 401, nên 401 ở trên là do token bị từ chối.
    @Test
    void accessToken_asBearer_onVaults_isAccepted_control() throws Exception {
        mockMvc.perform(get("/api/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isOk());
    }

    // ---------- (b) access token KHÔNG dùng được làm token xác nhận ----------

    @Test
    void accessToken_asConfirmToken_redirectsWithConfirmerTokenInvalid() throws Exception {
        String accessToken = accessToken();

        // SecurityConfig trên main chưa permitAll /api/dms/confirm (PR hạ tầng riêng), nên gửi kèm header để
        // request tới được controller; header không ảnh hưởng việc kiểm tham số token.
        mockMvc.perform(get("/api/dms/confirm")
                        .param("token", accessToken)
                        .param("decision", "UNREACHABLE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, INVALID_REDIRECT));

        verifyNoInteractions(dmsConfigRepository, dmsConfirmerRepository, dmsActivationProcessor, auditLogService);
    }

    // Token đúng khoá, đúng purpose nhưng audience có cả legacyvault-api -> vẫn bị từ chối.
    @Test
    void tokenWithBothAudiences_isRejected() throws Exception {
        String token = sign(JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(DmsConfirmerServiceImpl.CONFIRM_AUDIENCE, JwtConfig.AUDIENCE))
                .subject(confirmerId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim(DmsConfirmerServiceImpl.PURPOSE_CLAIM, DmsConfirmerServiceImpl.PURPOSE_DMS_CONFIRM)
                .claim(DmsConfirmerServiceImpl.DECISION_CLAIM, DmsConfirmerDecision.UNREACHABLE.name())
                .build());

        mockMvc.perform(get("/api/dms/confirm")
                        .param("token", token)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, INVALID_REDIRECT));

        verifyNoInteractions(dmsConfigRepository, dmsConfirmerRepository, dmsActivationProcessor, auditLogService);
    }

    // Đối chứng: token xác nhận thật qua được decoder riêng (tới bước tra confirmer trong DB).
    @Test
    void realConfirmToken_passesDecoder_control() throws Exception {
        String confirmToken = dmsConfirmerService.generateConfirmationToken(confirmerId, DmsConfirmerDecision.ALIVE);
        when(dmsConfirmerRepository.existsByLastTokenJti(any())).thenReturn(false);
        when(dmsConfirmerRepository.findById(confirmerId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dms/confirm")
                        .param("token", confirmToken)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, INVALID_REDIRECT));

        verify(dmsConfirmerRepository).findById(confirmerId);
    }

    // Access token giống TokenServiceImpl.generateAccessToken.
    private String accessToken() {
        Instant now = Instant.now();
        return sign(JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(ownerId.toString())
                .claim("roles", List.of("OWNER"))
                .claim("authorities", List.of("VAULT_READ"))
                .build());
    }

    private String sign(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
