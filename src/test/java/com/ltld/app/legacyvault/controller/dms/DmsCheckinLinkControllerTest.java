package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.DmsCheckinLinkController;
import com.ltld.app.legacyvault.dto.dmsdto.OneClickCheckinResponse;
import com.ltld.app.legacyvault.exception.DmsCheckinLinkException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsOneClickCheckinService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2: POST /api/dms/checkin-link gọi không kèm JWT (SecurityConfig thật), trả JSON như API thường;
 * lỗi check-in link có thêm data.reason cho FE (m5).
 */
@WebMvcTest(DmsCheckinLinkController.class)
@Import(SecurityConfig.class)
class DmsCheckinLinkControllerTest {

    private static final String URL = "/api/dms/checkin-link";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DmsOneClickCheckinService dmsOneClickCheckinService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    @Test
    void post_validToken_withoutAuthorization_returnsNextCycle() throws Exception {
        LocalDateTime due = LocalDateTime.of(2026, 12, 1, 9, 0);
        when(dmsOneClickCheckinService.checkInWithToken("good-token"))
                .thenReturn(OneClickCheckinResponse.builder().checkinDueAt(due).triggerAt(due.plusDays(7)).build());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"good-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkinDueAt").value("2026-12-01T09:00:00"))
                .andExpect(jsonPath("$.data.triggerAt").value("2026-12-08T09:00:00"));
    }

    @ParameterizedTest
    @EnumSource(DmsCheckinLinkException.Reason.class)
    void post_checkinLinkError_returnsErrorCodeAndReason(DmsCheckinLinkException.Reason reason) throws Exception {
        DmsCheckinLinkException ex = new DmsCheckinLinkException(reason, "nope");
        when(dmsOneClickCheckinService.checkInWithToken("token")).thenThrow(ex);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"token\"}"))
                .andExpect(status().is(ex.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ex.getErrorCode().name()))
                .andExpect(jsonPath("$.data.reason").value(reason.name()));
    }

    @Test
    void post_tokenInvalid_isBadRequestWithCheckinTokenInvalid() throws Exception {
        when(dmsOneClickCheckinService.checkInWithToken(any()))
                .thenThrow(new DmsCheckinLinkException(DmsCheckinLinkException.Reason.TOKEN_INVALID, "used"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"used\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CHECKIN_TOKEN_INVALID"));
    }

    @Test
    void post_blankToken_isValidationErrorAndServiceNotCalled() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verifyNoInteractions(dmsOneClickCheckinService);
    }

    // M1: link trong email là GET; GET tới API không được check-in (vẫn cần JWT như mọi /api/**).
    @Test
    void get_withoutAuthorization_isUnauthorizedAndServiceNotCalled() throws Exception {
        mockMvc.perform(get(URL).param("token", "good-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dmsOneClickCheckinService);
    }

    @Test
    void post_passesTokenToService() throws Exception {
        when(dmsOneClickCheckinService.checkInWithToken("abc")).thenReturn(OneClickCheckinResponse.builder().build());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"abc\"}"))
                .andExpect(status().isOk());

        verify(dmsOneClickCheckinService).checkInWithToken("abc");
    }
}
