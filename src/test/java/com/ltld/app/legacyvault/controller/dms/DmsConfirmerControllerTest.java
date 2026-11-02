package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** X2: GET /api/dms/confirm luôn redirect về trang FE /dms/confirm-done, kể cả khi lỗi. */
@WebMvcTest(DmsConfirmerController.class)
class DmsConfirmerControllerTest {

    private static final String ERROR_URL_PREFIX = "http://localhost:5173/dms/confirm-done?status=error&errorCode=";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DmsConfirmerService dmsConfirmerService;

    @Test
    void confirm_validToken_redirectsToSuccessPage() throws Exception {
        String redirect = "http://localhost:5173/dms/confirm-done?status=success&decision=ALIVE&dmsStatus=ACTIVE";
        when(dmsConfirmerService.respondAndBuildRedirectUrl("good-token", "ALIVE")).thenReturn(redirect);

        mockMvc.perform(get("/api/dms/confirm").param("token", "good-token").param("decision", "ALIVE"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, redirect));

        verify(dmsConfirmerService, never()).buildErrorRedirectUrl(any());
    }

    @Test
    void confirm_invalidToken_redirectsWithConfirmerTokenInvalid() throws Exception {
        when(dmsConfirmerService.respondAndBuildRedirectUrl("used-token", "UNREACHABLE"))
                .thenThrow(new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link has already been used."));
        when(dmsConfirmerService.buildErrorRedirectUrl(ErrorCode.CONFIRMER_TOKEN_INVALID))
                .thenReturn(ERROR_URL_PREFIX + "CONFIRMER_TOKEN_INVALID");

        mockMvc.perform(get("/api/dms/confirm").param("token", "used-token").param("decision", "UNREACHABLE"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "CONFIRMER_TOKEN_INVALID"));
    }

    @Test
    void confirm_unexpectedError_redirectsWithInternalError() throws Exception {
        when(dmsConfirmerService.respondAndBuildRedirectUrl("token", null))
                .thenThrow(new IllegalStateException("DB down"));
        when(dmsConfirmerService.buildErrorRedirectUrl(ErrorCode.INTERNAL_ERROR))
                .thenReturn(ERROR_URL_PREFIX + "INTERNAL_ERROR");

        mockMvc.perform(get("/api/dms/confirm").param("token", "token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "INTERNAL_ERROR"));
    }

    @Test
    void confirm_missingToken_stillRedirectsInsteadOfJson400() throws Exception {
        when(dmsConfirmerService.respondAndBuildRedirectUrl(isNull(), isNull()))
                .thenThrow(new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token is missing."));
        when(dmsConfirmerService.buildErrorRedirectUrl(ErrorCode.CONFIRMER_TOKEN_INVALID))
                .thenReturn(ERROR_URL_PREFIX + "CONFIRMER_TOKEN_INVALID");

        mockMvc.perform(get("/api/dms/confirm"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "CONFIRMER_TOKEN_INVALID"));
    }
}
