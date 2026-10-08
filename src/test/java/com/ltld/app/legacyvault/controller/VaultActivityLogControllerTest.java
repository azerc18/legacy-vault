package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.entity.VaultActivityLog;
import com.ltld.app.legacyvault.enums.ActionType;
import com.ltld.app.legacyvault.service.auditservice.VaultActivityLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VaultActivityLogController.class)
public class VaultActivityLogControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultActivityLogService vaultActivityLogService;

    @Test
    void getMyLogs_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        VaultActivityLog log1 = new VaultActivityLog();
        log1.setActionType(ActionType.CREATE_VAULT);
        log1.setDescription("Created vault");

        when(vaultActivityLogService.getUserLogs(eq(ownerId))).thenReturn(List.of(log1));

        mockMvc.perform(get("/api/vault-activity-logs")
                .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].actionType").value("CREATE_VAULT"))
                .andExpect(jsonPath("$.data[0].description").value("Created vault"));
    }
}
