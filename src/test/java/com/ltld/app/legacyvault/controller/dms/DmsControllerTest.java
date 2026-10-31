package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.DmsController;
import com.ltld.app.legacyvault.dto.dmsdto.DmsCheckinResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
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
    void getConfig_notConfigured_returns200WithConfiguredFalse() throws Exception {
        when(dmsConfigService.getConfig(ownerId))
                .thenReturn(DmsConfigResponse.builder().configured(false).hasUsableExecutor(true).build());

        mockMvc.perform(get("/api/dms").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dead Man's Switch configuration retrieved successfully"))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.hasUsableExecutor").value(true));
    }

    // ---------- GET /api/dms/check-ins ----------

    @Test
    void getCheckins_defaultPaging_returns200WithPage() throws Exception {
        DmsCheckinResponse row = DmsCheckinResponse.builder()
                .checkinType(DmsCheckinType.OWNER_RESPONDED)
                .occurredAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .build();
        when(dmsConfigService.getCheckins(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/dms/check-ins").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].checkinType").value("OWNER_RESPONDED"))
                .andExpect(jsonPath("$.data.content[0].occurredAt").value("2026-10-01T09:00:00"))
                .andExpect(jsonPath("$.data.content[0].channel").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(dmsConfigService).getCheckins(eq(ownerId),
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 20));
    }

    @Test
    void getCheckins_passesRequestedPage() throws Exception {
        when(dmsConfigService.getCheckins(eq(ownerId), any())).thenReturn(Page.empty(PageRequest.of(2, 5)));

        mockMvc.perform(get("/api/dms/check-ins").principal(principal).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(dmsConfigService).getCheckins(eq(ownerId),
                argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 5));
    }

    @Test
    void getCheckins_negativePage_returns400AndSkipsService() throws Exception {
        mockMvc.perform(get("/api/dms/check-ins").principal(principal).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_PARAMETER.name()))
                .andExpect(jsonPath("$.message").value("Invalid page number (must be >= 0)."));

        verify(dmsConfigService, never()).getCheckins(any(), any());
    }

    @Test
    void getCheckins_sizeOutOfRange_returns400AndSkipsService() throws Exception {
        for (String size : List.of("0", "101")) {
            mockMvc.perform(get("/api/dms/check-ins").principal(principal).param("size", size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_PARAMETER.name()))
                    .andExpect(jsonPath("$.message").value("Page size must be between 1 and 100."));
        }

        verify(dmsConfigService, never()).getCheckins(any(), any());
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
                "Create at least one vault before configuring the Dead Man's Switch.", HttpStatus.CONFLICT));

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
                .andExpect(jsonPath("$.message").value("Checked in successfully"))
                .andExpect(jsonPath("$.data.remindersSent").value(0));
    }

    @Test
    void checkIn_whenTriggered_returns409() throws Exception {
        when(dmsConfigService.checkIn(ownerId)).thenThrow(new DmsException(
                "The Dead Man's Switch has been triggered; check-in is no longer possible.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/dms/check-in").principal(principal))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The Dead Man's Switch has been triggered; check-in is no longer possible."));
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
