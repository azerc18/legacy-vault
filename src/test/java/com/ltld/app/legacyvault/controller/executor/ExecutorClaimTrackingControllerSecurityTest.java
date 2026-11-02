package com.ltld.app.legacyvault.controller.executor;

import com.ltld.app.legacyvault.controller.ExecutorController;
import com.ltld.app.legacyvault.dto.executordto.BeneficiaryClaimProgressResponse;
import com.ltld.app.legacyvault.enums.BeneficiaryClaimProgress;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorClaimTrackingService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorSelfService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// N2-exec: các mã trả về của API theo dõi người thụ hưởng và gửi nhắc nhở
@WebMvcTest(ExecutorController.class)
@Import(SecurityConfig.class)
class ExecutorClaimTrackingControllerSecurityTest {

    private static final String LIST_URL = "/api/executor-assignments/{aid}/vaults/{vid}/beneficiaries";
    private static final String REMIND_URL = LIST_URL + "/{bid}/reminders";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsAutomationService dmsAutomationService;
    @MockitoBean private ExecutorAssetService executorAssetService;
    @MockitoBean private ExecutorHandoverService executorHandoverService;
    @MockitoBean private ExecutorVerificationService executorVerificationService;
    @MockitoBean private VerificationExchangeService verificationExchangeService;
    @MockitoBean private ExecutorSelfService executorSelfService;
    @MockitoBean private ExecutorClaimTrackingService executorClaimTrackingService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID aid = UUID.randomUUID();
    private final UUID vid = UUID.randomUUID();
    private final UUID bid = UUID.randomUUID();

    private RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get(LIST_URL, aid, vid)).andExpect(status().isUnauthorized());
        verifyNoInteractions(executorClaimTrackingService);
    }

    @Test
    void list_valid_returns200WithMaskedRows() throws Exception {
        when(executorClaimTrackingService.listBeneficiaryProgress(aid, vid, userId.toString())).thenReturn(List.of(
                BeneficiaryClaimProgressResponse.builder().vaultBeneficiaryId(bid).maskedEmail("pe*****@example.com")
                        .status(BeneficiaryClaimProgress.PENDING).canRemind(true).build()));

        mockMvc.perform(get(LIST_URL, aid, vid).with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].maskedEmail").value("pe*****@example.com"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].canRemind").value(true));
    }

    @Test
    void list_vaultNotOwnedOrNotHandedOver_returns404() throws Exception {
        when(executorClaimTrackingService.listBeneficiaryProgress(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy Vault."));

        mockMvc.perform(get(LIST_URL, aid, vid).with(token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void list_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(LIST_URL, aid, "not-a-uuid").with(token())).andExpect(status().isBadRequest());
        verifyNoInteractions(executorClaimTrackingService);
    }

    @Test
    void remind_noToken_returns401() throws Exception {
        mockMvc.perform(post(REMIND_URL, aid, vid, bid).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(executorClaimTrackingService);
    }

    @Test
    void remind_valid_returns200() throws Exception {
        when(executorClaimTrackingService.sendReminder(eq(aid), eq(vid), eq(bid), eq(userId.toString())))
                .thenReturn(BeneficiaryClaimProgressResponse.builder().vaultBeneficiaryId(bid)
                        .status(BeneficiaryClaimProgress.PENDING).canRemind(false).build());

        mockMvc.perform(post(REMIND_URL, aid, vid, bid).with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canRemind").value(false));
    }

    @Test
    void remind_tooSoon_returns429() throws Exception {
        when(executorClaimTrackingService.sendReminder(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.REMINDER_TOO_SOON, "24h"));

        mockMvc.perform(post(REMIND_URL, aid, vid, bid).with(token()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_TOO_SOON"));
    }

    @Test
    void remind_alreadyResponded_returns409() throws Exception {
        when(executorClaimTrackingService.sendReminder(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CLAIM_ALREADY_RESPONDED, "responded"));

        mockMvc.perform(post(REMIND_URL, aid, vid, bid).with(token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_ALREADY_RESPONDED"));
    }
}
