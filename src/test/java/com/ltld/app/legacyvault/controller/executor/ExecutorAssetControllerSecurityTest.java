package com.ltld.app.legacyvault.controller.executor;

import com.ltld.app.legacyvault.controller.ExecutorController;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultAssetResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultResponse;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorVerificationService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutorController.class)
@Import(SecurityConfig.class)
class ExecutorAssetControllerSecurityTest {

    private static final String GET_VAULTS_URL = "/api/executor-assignments/{id}/vaults";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsAutomationService dmsAutomationService;
    @MockitoBean private ExecutorAssetService executorAssetService;
    @MockitoBean private ExecutorHandoverService executorHandoverService;
    @MockitoBean private ExecutorVerificationService executorVerificationService;
    @MockitoBean private VerificationExchangeService verificationExchangeService;
    @MockitoBean private com.ltld.app.legacyvault.service.executorservice.ExecutorSelfService executorSelfService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    @Test
    void getAssignedVaults_noToken_returns401() throws Exception {
        mockMvc.perform(get(GET_VAULTS_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(executorAssetService);
    }

    @Test
    void getAssignedVaults_validRequest_returns200WithVaults() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();

        ExecutorVaultAssetResponse asset = ExecutorVaultAssetResponse.builder()
                .assetId(UUID.randomUUID())
                .assetType(AssetType.CRYPTO_WALLET)
                .assetName("Ethereum Ledger")
                .build();
        ExecutorVaultBeneficiaryResponse beneficiary = ExecutorVaultBeneficiaryResponse.builder()
                .id(UUID.randomUUID())
                .fullName("Le Thi B")
                .build();
        ExecutorVaultResponse vault = ExecutorVaultResponse.builder()
                .vaultId(vaultId)
                .name("Két Gia Đình")
                .status(VaultStatus.ACTIVE)
                .handoverInstructions("Chuyển giao cho con gái")
                .beneficiary(beneficiary)
                .assets(List.of(asset))
                .build();
        ExecutorAssignedVaultsResponse response = ExecutorAssignedVaultsResponse.builder()
                .assignmentId(assignmentId)
                .ownerFullName("Nguyen Van A")
                .vaults(List.of(vault))
                .build();

        when(executorAssetService.getAssignedVaults(eq(assignmentId), eq(userId.toString()))).thenReturn(response);

        mockMvc.perform(get(GET_VAULTS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.ownerFullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.vaults[0].vaultId").value(vaultId.toString()))
                .andExpect(jsonPath("$.data.vaults[0].name").value("Két Gia Đình"))
                .andExpect(jsonPath("$.data.vaults[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.vaults[0].handoverInstructions").value("Chuyển giao cho con gái"))
                .andExpect(jsonPath("$.data.vaults[0].beneficiary.fullName").value("Le Thi B"))
                .andExpect(jsonPath("$.data.vaults[0].assets[0].assetName").value("Ethereum Ledger"))
                .andExpect(content().string(not(containsString("encryptedSecret"))))
                .andExpect(content().string(not(containsString("notesEncrypted"))))
                .andExpect(content().string(not(containsString("encryptionKeyRef"))))
                .andExpect(content().string(not(containsString("attachmentUrl"))))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void getAssignedVaults_emptyVaults_returns200WithEmptyList() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExecutorAssignedVaultsResponse response = ExecutorAssignedVaultsResponse.builder()
                .assignmentId(assignmentId)
                .ownerFullName("Nguyen Van A")
                .vaults(Collections.emptyList())
                .build();
        when(executorAssetService.getAssignedVaults(eq(assignmentId), eq(userId.toString()))).thenReturn(response);

        mockMvc.perform(get(GET_VAULTS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vaults").isArray())
                .andExpect(jsonPath("$.data.vaults").isEmpty());
    }

    @Test
    void getAssignedVaults_pendingBeneficiary_exposesFlagButNotEmail() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExecutorVaultResponse vault = ExecutorVaultResponse.builder()
                .vaultId(UUID.randomUUID())
                .name("Két chờ đăng ký")
                .status(VaultStatus.ACTIVE)
                .beneficiary(null)
                .beneficiaryPending(true)
                .assets(Collections.emptyList())
                .build();
        ExecutorAssignedVaultsResponse response = ExecutorAssignedVaultsResponse.builder()
                .assignmentId(assignmentId)
                .ownerFullName("Nguyen Van A")
                .vaults(List.of(vault))
                .build();
        when(executorAssetService.getAssignedVaults(eq(assignmentId), eq(userId.toString()))).thenReturn(response);

        mockMvc.perform(get(GET_VAULTS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vaults[0].beneficiaryPending").value(true))
                .andExpect(jsonPath("$.data.vaults[0].beneficiary").doesNotExist())
                .andExpect(content().string(not(containsString("beneficiaryEmail"))))
                .andExpect(content().string(not(containsString("@"))));
    }

    @Test
    void getAssignedVaults_inactiveAssignment_returns403() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(executorAssetService.getAssignedVaults(eq(assignmentId), eq(userId.toString())))
                .thenThrow(new ExecutorNotActiveException("Access is not available yet"));

        mockMvc.perform(get(GET_VAULTS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is not available yet"));
    }

    @Test
    void getAssignedVaults_assignmentNotFound_returns404() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(executorAssetService.getAssignedVaults(eq(assignmentId), eq(userId.toString())))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        mockMvc.perform(get(GET_VAULTS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAssignedVaults_callerMismatchIdor_returns404Not403() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        when(executorAssetService.getAssignedVaults(eq(assignmentId), eq(attackerId.toString())))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        mockMvc.perform(get(GET_VAULTS_URL, assignmentId).with(token(attackerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
