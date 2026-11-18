package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminSystemPolicyController;
import com.ltld.app.legacyvault.controller.DmsController;
import com.ltld.app.legacyvault.dto.systempolicydto.AdminDmsPolicyResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.DmsPolicy;
import com.ltld.app.legacyvault.dto.systempolicydto.DmsPolicyImpactResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.SystemInfoResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.UpdateDmsPolicyRequest;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.service.systempolicyservice.DmsPolicyService;
import com.ltld.app.legacyvault.service.systempolicyservice.SystemInfoService;
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

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trang Chính sách hệ thống: chỉ ADMIN_MANAGE đọc / sửa; Owner chỉ đọc được khoảng cho phép qua /api/dms/policy. */
@WebMvcTest({AdminSystemPolicyController.class, DmsController.class})
@Import(SecurityConfig.class)
class AdminSystemPolicyControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String VALID_BODY = """
            {"checkinIntervalDays":{"min":7,"defaultValue":30,"max":180},
             "gracePeriodDays":{"min":1,"defaultValue":7,"max":90},
             "reminderCount":{"min":1,"defaultValue":3,"max":10},
             "backupActivationWaitDays":21}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsPolicyService dmsPolicyService;
    @MockitoBean private SystemInfoService systemInfoService;
    @MockitoBean private DmsConfigService dmsConfigService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static RequestPostProcessor admin() {
        return token(ADMIN_ID, "ROLE_ADMIN", "ADMIN_MANAGE");
    }

    private static AdminDmsPolicyResponse adminResponse() {
        return new AdminDmsPolicyResponse(DmsPolicy.DEFAULTS, DmsPolicy.DEFAULTS, 0, null, null);
    }

    @Test
    void getDms_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/system-policies/dms")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dmsPolicyService);
    }

    @Test
    void getDms_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/system-policies/dms").with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dmsPolicyService);
    }

    @Test
    void getDms_admin_returnsPolicyAndNoStore() throws Exception {
        when(dmsPolicyService.getForAdmin()).thenReturn(adminResponse());

        mockMvc.perform(get("/api/admin/system-policies/dms").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.policy.checkinIntervalDays.defaultValue").value(30))
                .andExpect(jsonPath("$.data.policy.backupActivationWaitDays").value(14));
    }

    @Test
    void putDms_owner_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/system-policies/dms").with(token(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dmsPolicyService);
    }

    @Test
    void putDms_missingField_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/admin/system-policies/dms").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkinIntervalDays\":{\"min\":1,\"max\":365},\"backupActivationWaitDays\":14}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dmsPolicyService);
    }

    @Test
    void putDms_admin_passesAdminIdAndRequest() throws Exception {
        when(dmsPolicyService.update(eq(ADMIN_ID), any(UpdateDmsPolicyRequest.class))).thenReturn(adminResponse());

        mockMvc.perform(put("/api/admin/system-policies/dms").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        verify(dmsPolicyService).update(eq(ADMIN_ID), argThat(r -> r.checkinIntervalDays().max() == 180
                && r.backupActivationWaitDays() == 21));
    }

    @Test
    void putDms_invalidPolicy_returns400WithErrorCode() throws Exception {
        when(dmsPolicyService.update(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.SYSTEM_POLICY_INVALID, "minimum is greater than maximum"));

        mockMvc.perform(put("/api/admin/system-policies/dms").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_POLICY_INVALID"));
    }

    @Test
    void previewImpact_owner_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/system-policies/dms/impact").with(token(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dmsPolicyService);
    }

    @Test
    void previewImpact_admin_returnsCountAndNoStore_withoutSaving() throws Exception {
        when(dmsPolicyService.previewImpact(any(UpdateDmsPolicyRequest.class))).thenReturn(new DmsPolicyImpactResponse(4));

        mockMvc.perform(post("/api/admin/system-policies/dms/impact").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.configsOutsideLimits").value(4));
        verify(dmsPolicyService).previewImpact(argThat(r -> r.checkinIntervalDays().min() == 7));
        verify(dmsPolicyService, never()).update(any(), any());
    }

    @Test
    void previewImpact_missingField_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/admin/system-policies/dms/impact").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"backupActivationWaitDays\":14}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dmsPolicyService);
    }

    @Test
    void systemInfo_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/system-policies/system-info").with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(systemInfoService);
    }

    @Test
    void systemInfo_admin_returnsInfo() throws Exception {
        when(systemInfoService.get()).thenReturn(new SystemInfoResponse(
                new SystemInfoResponse.Crypto("AES-256-GCM", true, "BCrypt", "HMAC-SHA256", false, false),
                new SystemInfoResponse.Storage("s3", "legacyvault", true),
                new SystemInfoResponse.Backup(null, null, null), "20261118.1200"));

        mockMvc.perform(get("/api/admin/system-policies/system-info").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.crypto.dataEncryption").value("AES-256-GCM"))
                .andExpect(jsonPath("$.data.storage.bucket").value("legacyvault"))
                .andExpect(jsonPath("$.data.schemaVersion").value("20261118.1200"));
    }

    @Test
    void ownerPolicy_owner_returnsCurrentPolicy() throws Exception {
        when(dmsPolicyService.current()).thenReturn(DmsPolicy.DEFAULTS);

        mockMvc.perform(get("/api/dms/policy").with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminderCount.max").value(10));
    }

    @Test
    void ownerPolicy_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/dms/policy")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dmsPolicyService);
    }
}
