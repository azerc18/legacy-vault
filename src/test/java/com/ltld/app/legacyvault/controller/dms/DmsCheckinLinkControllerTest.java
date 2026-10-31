package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
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

@WebMvcTest(DmsCheckinLinkController.class)
class DmsCheckinLinkControllerTest {

    private static final String ERROR_URL_PREFIX = "http://localhost:5173/checkin/done?status=error&errorCode=";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DmsOneClickCheckinService dmsOneClickCheckinService;

    @Test
    void checkInByEmailLink_validToken_redirectsToFrontend() throws Exception {
        String redirect = "http://localhost:5173/checkin/done?status=success&nextDue=2026-12-01T09%3A00";
        when(dmsOneClickCheckinService.checkInAndBuildRedirectUrl("good-token")).thenReturn(redirect);

        mockMvc.perform(get("/api/dms/checkin-link").param("token", "good-token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, redirect));

        verify(dmsOneClickCheckinService, never()).buildErrorRedirectUrl(any());
    }

    @Test
    void checkInByEmailLink_invalidToken_redirectsWithCheckinTokenInvalid() throws Exception {
        when(dmsOneClickCheckinService.checkInAndBuildRedirectUrl("used-token"))
                .thenThrow(new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "This check-in link has already been used."));
        when(dmsOneClickCheckinService.buildErrorRedirectUrl(ErrorCode.CHECKIN_TOKEN_INVALID))
                .thenReturn(ERROR_URL_PREFIX + "CHECKIN_TOKEN_INVALID");

        mockMvc.perform(get("/api/dms/checkin-link").param("token", "used-token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "CHECKIN_TOKEN_INVALID"));
    }

    @Test
    void checkInByEmailLink_otherBusinessError_redirectsWithItsErrorCode() throws Exception {
        when(dmsOneClickCheckinService.checkInAndBuildRedirectUrl("token"))
                .thenThrow(new DmsException(ErrorCode.CONFLICT, "The Dead Man's Switch has been triggered."));
        when(dmsOneClickCheckinService.buildErrorRedirectUrl(ErrorCode.CONFLICT))
                .thenReturn(ERROR_URL_PREFIX + "CONFLICT");

        mockMvc.perform(get("/api/dms/checkin-link").param("token", "token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "CONFLICT"));
    }

    @Test
    void checkInByEmailLink_unexpectedError_redirectsWithInternalError() throws Exception {
        when(dmsOneClickCheckinService.checkInAndBuildRedirectUrl("token"))
                .thenThrow(new IllegalStateException("DB down"));
        when(dmsOneClickCheckinService.buildErrorRedirectUrl(ErrorCode.INTERNAL_ERROR))
                .thenReturn(ERROR_URL_PREFIX + "INTERNAL_ERROR");

        mockMvc.perform(get("/api/dms/checkin-link").param("token", "token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "INTERNAL_ERROR"));
    }

    @Test
    void checkInByEmailLink_missingToken_stillRedirectsInsteadOfJson400() throws Exception {
        when(dmsOneClickCheckinService.checkInAndBuildRedirectUrl(isNull()))
                .thenThrow(new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Check-in token is missing."));
        when(dmsOneClickCheckinService.buildErrorRedirectUrl(ErrorCode.CHECKIN_TOKEN_INVALID))
                .thenReturn(ERROR_URL_PREFIX + "CHECKIN_TOKEN_INVALID");

        mockMvc.perform(get("/api/dms/checkin-link"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, ERROR_URL_PREFIX + "CHECKIN_TOKEN_INVALID"));
    }
}
