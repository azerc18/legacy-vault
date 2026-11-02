package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.DmsConfirmerController;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmationDecisionResponse;
import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** X2 (C1/M1): quyết định đi bằng POST /api/dms/confirm {token, decision}; GET không còn thay đổi gì. */
@WebMvcTest(DmsConfirmerController.class)
class DmsConfirmerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DmsConfirmerService dmsConfirmerService;

    @Test
    void confirm_post_usesDecisionFromBody() throws Exception {
        when(dmsConfirmerService.respondByToken(eq("good-token"), eq(DmsConfirmerDecision.ALIVE), any()))
                .thenReturn(DmsConfirmationDecisionResponse.builder()
                        .decision(DmsConfirmerDecision.ALIVE).dmsStatus(DmsStatus.ACTIVE).build());

        mockMvc.perform(post("/api/dms/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"good-token\",\"decision\":\"ALIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("ALIVE"))
                .andExpect(jsonPath("$.data.dmsStatus").value("ACTIVE"));

        verify(dmsConfirmerService).respondByToken(eq("good-token"), eq(DmsConfirmerDecision.ALIVE), any());
    }

    @Test
    void confirm_post_invalidToken_returns400WithConfirmerTokenInvalid() throws Exception {
        when(dmsConfirmerService.respondByToken(eq("used-token"), eq(DmsConfirmerDecision.UNREACHABLE), any()))
                .thenThrow(new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link has already been used."));

        mockMvc.perform(post("/api/dms/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"used-token\",\"decision\":\"UNREACHABLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMER_TOKEN_INVALID"));
    }

    @Test
    void confirm_post_missingDecision_returns400_andServiceNotCalled() throws Exception {
        mockMvc.perform(post("/api/dms/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"good-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verify(dmsConfirmerService, never()).respondByToken(any(), any(), any());
    }

    @Test
    void confirm_post_unknownDecision_returns400_andServiceNotCalled() throws Exception {
        mockMvc.perform(post("/api/dms/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"good-token\",\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());

        verify(dmsConfirmerService, never()).respondByToken(any(), any(), any());
    }

    // C1: trình quét link của hộp thư chỉ gọi GET; GET không còn endpoint nào xử lý quyết định.
    @Test
    void confirm_get_isNotHandled_andChangesNothing() throws Exception {
        mockMvc.perform(get("/api/dms/confirm").param("token", "good-token").param("decision", "UNREACHABLE"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(dmsConfirmerService);
    }
}
