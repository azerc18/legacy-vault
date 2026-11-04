package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.DmsConfirmerController;
import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsActivationProcessor;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerServiceImpl;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.EmailSender;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * X2 (giống F2): token xác nhận chéo và access token không dùng thay cho nhau được.
 * SecurityConfig, JwtConfig (JwtDecoder/JwtEncoder thật) và DmsConfirmerServiceImpl thật; chỉ mock tầng dữ liệu.
 */
@WebMvcTest(
        controllers = {VaultController.class, DmsConfirmerController.class},
        properties = {
                "security.jwt.secret=dms-confirm-security-test-secret-at-least-32-bytes!!",
                "app.frontend.base-url=http://fe.test"
        })
@Import({SecurityConfig.class, JwtConfig.class, DmsConfirmerServiceImpl.class})
class DmsConfirmTokenSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private DmsConfirmerService dmsConfirmerService;

    @MockitoBean private VaultService vaultService;
    @MockitoBean private DmsConfigRepository dmsConfigRepository;
    @MockitoBean private DmsConfirmerRepository dmsConfirmerRepository;
    @MockitoBean private ExecutorAssignmentRepository executorAssignmentRepository;
    @MockitoBean private DmsActivationProcessor dmsActivationProcessor;
    @MockitoBean private EntitlementService entitlementService;
    @MockitoBean private EmailSender emailSender;
    @MockitoBean private AuditLogService auditLogService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID confirmerId = UUID.randomUUID();
    private final LocalDateTime deadlineAt = LocalDateTime.now().plusHours(72);

    // ---------- (a) token xác nhận KHÔNG dùng được như access token ----------

    @Test
    void confirmToken_asBearer_onVaults_returns401() throws Exception {
        String confirmToken = dmsConfirmerService.generateConfirmationToken(confirmerId, deadlineAt);

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
    void accessToken_asConfirmToken_returns400ConfirmerTokenInvalid() throws Exception {
        mockMvc.perform(confirm(accessToken(), "UNREACHABLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMER_TOKEN_INVALID"));

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
                .build());

        mockMvc.perform(confirm(token, "UNREACHABLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMER_TOKEN_INVALID"));

        verifyNoInteractions(dmsConfigRepository, dmsConfirmerRepository, dmsActivationProcessor, auditLogService);
    }

    // Đối chứng: token xác nhận thật qua được decoder riêng (tới bước tra confirmer trong DB).
    @Test
    void realConfirmToken_passesDecoder_control() throws Exception {
        String confirmToken = dmsConfirmerService.generateConfirmationToken(confirmerId, deadlineAt);
        when(dmsConfirmerRepository.findById(confirmerId)).thenReturn(Optional.empty());

        mockMvc.perform(confirm(confirmToken, "ALIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMER_TOKEN_INVALID"));

        verify(dmsConfirmerRepository).findById(confirmerId);
    }

    // M4: POST /api/dms/confirm đã permitAll nên gửi KHÔNG có Bearer, đúng như người xác nhận chéo (ẩn danh)
    private MockHttpServletRequestBuilder confirm(String token, String decision) {
        return post("/api/dms/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"decision\":\"" + decision + "\"}");
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
