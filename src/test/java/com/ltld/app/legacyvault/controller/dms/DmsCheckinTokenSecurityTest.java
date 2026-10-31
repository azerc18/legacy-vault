package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinServiceImpl;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2: token check-in một chạm và access token không dùng thay cho nhau được.
 * SecurityConfig, JwtConfig (JwtDecoder/JwtEncoder thật) và DmsOneClickCheckinServiceImpl thật; chỉ mock tầng dữ liệu.
 */
@WebMvcTest(
        controllers = {VaultController.class, DmsCheckinLinkController.class},
        properties = {
                "security.jwt.secret=dms-checkin-security-test-secret-at-least-32-bytes!!",
                "app.frontend.base-url=http://fe.test",
                "app.backend.base-url=http://api.test"
        })
@Import({SecurityConfig.class, JwtConfig.class, DmsOneClickCheckinServiceImpl.class})
class DmsCheckinTokenSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private DmsOneClickCheckinService dmsOneClickCheckinService;

    @MockitoBean private VaultService vaultService;
    @MockitoBean private DmsConfigRepository dmsConfigRepository;
    @MockitoBean private DmsCheckinRepository dmsCheckinRepository;
    @MockitoBean private ExecutorAssignmentRepository executorAssignmentRepository;
    @MockitoBean private AuditLogService auditLogService;

    private final UUID ownerId = UUID.randomUUID();

    // ---------- (a) token check-in KHÔNG dùng được như access token ----------

    @Test
    void checkinToken_asBearer_onOwnerVaults_returns401() throws Exception {
        String checkinToken = dmsOneClickCheckinService.generateCheckinToken(ownerId);

        mockMvc.perform(get("/api/owner/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + checkinToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkinToken_asBearer_onVaults_returns401() throws Exception {
        String checkinToken = dmsOneClickCheckinService.generateCheckinToken(ownerId);

        mockMvc.perform(get("/api/vaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + checkinToken))
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
    void accessToken_asCheckinLinkToken_redirectsWithCheckinTokenInvalid() throws Exception {
        String accessToken = accessToken();

        // SecurityConfig hiện chưa permitAll /api/dms/checkin-link (ngoài phạm vi F2 lượt này), nên gửi kèm
        // header để request tới được controller; header không ảnh hưởng việc kiểm tham số token.
        mockMvc.perform(get("/api/dms/checkin-link")
                        .param("token", accessToken)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "http://fe.test/checkin/done?status=error&errorCode=CHECKIN_TOKEN_INVALID"));

        verifyNoInteractions(dmsConfigRepository, dmsCheckinRepository, auditLogService);
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
