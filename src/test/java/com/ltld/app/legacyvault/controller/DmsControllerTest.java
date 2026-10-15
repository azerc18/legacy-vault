package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DmsController.class)
public class DmsControllerTest {

    private static final String VALID_BODY =
            "{\"checkinIntervalDays\":30,\"gracePeriodDays\":7,\"reminderCount\":3}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DmsConfigService dmsConfigService;

    private final UUID ownerId = UUID.randomUUID();
    private final Principal principal = () -> ownerId.toString();

    private static DmsConfigResponse sampleResponse() {
        LocalDateTime last = LocalDateTime.of(2026, 10, 1, 9, 0);
        return DmsConfigResponse.builder()
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .lastCheckinAt(last)
                .checkinDueAt(last.plusDays(30))
                .triggerAt(last.plusDays(37))
                .nextReminderAt(last.plusDays(30))
                .remindersSent(0)
                .build();
    }

    @Test
    void getConfig_returns200WithSchedule() throws Exception {
        when(dmsConfigService.getConfig(ownerId)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/dms").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.checkinIntervalDays").value(30))
                .andExpect(jsonPath("$.data.checkinDueAt").value("2026-10-31T09:00:00"))
                .andExpect(jsonPath("$.data.triggerAt").value("2026-11-07T09:00:00"));
    }

    @Test
    void getConfig_notConfigured_returns404() throws Exception {
        when(dmsConfigService.getConfig(ownerId))
                .thenThrow(new DmsException("Chưa cấu hình Dead Man's Switch.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/dms").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa cấu hình Dead Man's Switch."));
    }

    @Test
    void upsert_newConfig_returns201() throws Exception {
        when(dmsConfigService.upsertConfig(eq(ownerId), any()))
                .thenReturn(new DmsConfigService.UpsertResult(sampleResponse(), true));

        mockMvc.perform(put("/api/dms").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gracePeriodDays").value(7));

        verify(dmsConfigService).upsertConfig(eq(ownerId), argThat(r ->
                r.getCheckinIntervalDays() == 30 && r.getGracePeriodDays() == 7 && r.getReminderCount() == 3));
    }

    @Test
    void upsert_existingConfig_returns200() throws Exception {
        when(dmsConfigService.upsertConfig(eq(ownerId), any()))
                .thenReturn(new DmsConfigService.UpsertResult(sampleResponse(), false));

        mockMvc.perform(put("/api/dms").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void upsert_invalidBody_returns400AndSkipsService() throws Exception {
        mockMvc.perform(put("/api/dms").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkinIntervalDays\":0,\"gracePeriodDays\":91}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.checkinIntervalDays").exists())
                .andExpect(jsonPath("$.data.gracePeriodDays").exists())
                .andExpect(jsonPath("$.data.reminderCount").exists());

        verify(dmsConfigService, never()).upsertConfig(any(), any());
    }

    @Test
    void upsert_withoutVault_returns409() throws Exception {
        when(dmsConfigService.upsertConfig(eq(ownerId), any())).thenThrow(new DmsException(
                "Cần tạo ít nhất một Vault trước khi cấu hình Dead Man's Switch.", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/dms").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void checkIn_returns200() throws Exception {
        when(dmsConfigService.checkIn(ownerId)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/dms/check-in").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Check-in thành công"))
                .andExpect(jsonPath("$.data.remindersSent").value(0));
    }

    @Test
    void checkIn_whenTriggered_returns409() throws Exception {
        when(dmsConfigService.checkIn(ownerId)).thenThrow(new DmsException(
                "Dead Man's Switch đã được kích hoạt, không thể check-in.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/dms/check-in").principal(principal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dead Man's Switch đã được kích hoạt, không thể check-in."));
    }

    @Test
    void checkIn_optimisticLockConflict_returns409() throws Exception {
        when(dmsConfigService.checkIn(ownerId))
                .thenThrow(new ObjectOptimisticLockingFailureException("DmsConfig", UUID.randomUUID()));

        mockMvc.perform(post("/api/dms/check-in").principal(principal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
