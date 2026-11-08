package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminBeneficiaryLockController;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryLockAdminService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBeneficiaryLockController.class)
@Import(SecurityConfig.class)
class AdminBeneficiaryLockControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BeneficiaryLockAdminService beneficiaryLockAdminService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static RequestPostProcessor admin() {
        return token(ADMIN_ID, "ROLE_ADMIN", "ADMIN_MANAGE");
    }

    private static String body(UUID vaultId, UUID beneficiaryId) {
        return "{\"vaultId\":\"" + vaultId + "\",\"beneficiaryId\":\"" + beneficiaryId + "\",\"reason\":\"called support\"}";
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/beneficiary-locks")).andExpect(status().isUnauthorized());
        verifyNoInteractions(beneficiaryLockAdminService);
    }

    @Test
    void list_beneficiary_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/beneficiary-locks").with(token(UUID.randomUUID(), "ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(beneficiaryLockAdminService);
    }

    @Test
    void list_admin_returns200() throws Exception {
        when(beneficiaryLockAdminService.listActiveLocks()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/beneficiary-locks").with(admin())).andExpect(status().isOk());
    }

    @Test
    void unlock_beneficiaryCannotUnlockThemselves_returns403() throws Exception {
        UUID me = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/beneficiary-locks/unlock").with(token(me, "ROLE_BENEFICIARY"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID(), me)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(beneficiaryLockAdminService);
    }

    @Test
    void unlock_admin_passesAdminIdAndReason() throws Exception {
        UUID vaultId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/beneficiary-locks/unlock").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(vaultId, beneficiaryId)))
                .andExpect(status().isOk());
        verify(beneficiaryLockAdminService).unlock(ADMIN_ID, vaultId, beneficiaryId, "called support");
    }

    @Test
    void unlock_missingIds_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/beneficiary-locks/unlock").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(beneficiaryLockAdminService);
    }

    @Test
    void unlock_notLocked_returns404() throws Exception {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND, "This beneficiary is not locked for this vault."))
                .when(beneficiaryLockAdminService).unlock(any(), any(), any(), any());
        mockMvc.perform(post("/api/admin/beneficiary-locks/unlock").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
