package com.ltld.app.legacyvault.controller.executor;

import com.ltld.app.legacyvault.controller.ExecutorTaskController;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.executorservice.ExecutorTaskService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// F5: mã trả về của API checklist Executor và màn hình theo dõi của Beneficiary
@WebMvcTest(ExecutorTaskController.class)
@Import(SecurityConfig.class)
class ExecutorTaskControllerSecurityTest {

    private static final String LIST_URL = "/api/executor-assignments/{aid}/tasks";
    private static final String UPDATE_URL = LIST_URL + "/{tid}";
    private static final String BENEFICIARY_URL = "/api/beneficiaries/vaults/{vid}/executor-tasks";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExecutorTaskService executorTaskService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final UUID userId = UUID.randomUUID();
    private final UUID aid = UUID.randomUUID();
    private final UUID tid = UUID.randomUUID();
    private final UUID vid = UUID.randomUUID();

    private RequestPostProcessor token() {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    private RequestPostProcessor beneficiaryToken() {
        return jwt().jwt(j -> j.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_BENEFICIARY"));
    }

    private static ExecutorTaskResponse row(ExecutorTaskStatus status) {
        return ExecutorTaskResponse.builder().id(UUID.randomUUID()).platform("Facebook")
                .wish(ExecutorTaskWish.MEMORIALIZE).status(status).build();
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get(LIST_URL, aid)).andExpect(status().isUnauthorized());
        verifyNoInteractions(executorTaskService);
    }

    @Test
    void list_valid_returns200() throws Exception {
        when(executorTaskService.listForExecutor(aid, userId.toString())).thenReturn(List.of(row(ExecutorTaskStatus.TODO)));

        mockMvc.perform(get(LIST_URL, aid).with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].platform").value("Facebook"))
                .andExpect(jsonPath("$.data[0].status").value("TODO"));
    }

    @Test
    void update_valid_returns200() throws Exception {
        when(executorTaskService.update(eq(aid), eq(tid), any(), eq(userId.toString())))
                .thenReturn(row(ExecutorTaskStatus.DONE));

        mockMvc.perform(patch(UPDATE_URL, aid, tid).with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\",\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void update_missingStatus_returns400AndSkipsService() throws Exception {
        mockMvc.perform(patch(UPDATE_URL, aid, tid).with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(executorTaskService);
    }

    @Test
    void update_invalidStatusValue_returns400() throws Exception {
        mockMvc.perform(patch(UPDATE_URL, aid, tid).with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(executorTaskService);
    }

    @Test
    void update_taskOfAnotherOwner_returns404() throws Exception {
        when(executorTaskService.update(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy việc cần xử lý."));

        mockMvc.perform(patch(UPDATE_URL, aid, tid).with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void update_archivedVault_returns409() throws Exception {
        when(executorTaskService.update(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.VAULT_STATE_CONFLICT, "archived"));

        mockMvc.perform(patch(UPDATE_URL, aid, tid).with(token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void beneficiary_withoutBeneficiaryRole_returns403() throws Exception {
        mockMvc.perform(get(BENEFICIARY_URL, vid).with(token())).andExpect(status().isForbidden());
        verifyNoInteractions(executorTaskService);
    }

    @Test
    void beneficiary_valid_returns200() throws Exception {
        when(executorTaskService.listForBeneficiary(vid, userId)).thenReturn(List.of(row(ExecutorTaskStatus.DOING)));

        mockMvc.perform(get(BENEFICIARY_URL, vid).with(beneficiaryToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("DOING"));
    }

    @Test
    void beneficiary_notBeneficiaryOfVault_returns404() throws Exception {
        when(executorTaskService.listForBeneficiary(vid, userId))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy Vault."));

        mockMvc.perform(get(BENEFICIARY_URL, vid).with(beneficiaryToken())).andExpect(status().isNotFound());
    }
}
