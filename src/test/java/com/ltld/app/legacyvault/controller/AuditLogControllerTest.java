package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.ActionType;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
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

@WebMvcTest(AuditLogController.class)
public class AuditLogControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuditLogService auditLogService;

    @Test
    void getMyLogs_ReturnsOk() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Principal mockPrincipal = () -> ownerId.toString();

        // Giả lập trong CSDL đang có 1 dòng log
        AuditLog log1 = new AuditLog();
        log1.setActionType(ActionType.CREATE_VAULT);
        log1.setDescription("Created vault");

        when(auditLogService.getUserLogs(eq(ownerId))).thenReturn(List.of(log1));

        // Bắn API GET
        mockMvc.perform(get("/api/audit-logs")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // Kiểm tra xem dữ liệu JSON trả về có khớp với dữ liệu giả lập không
                .andExpect(jsonPath("$.data[0].actionType").value("CREATE_VAULT"))
                .andExpect(jsonPath("$.data[0].description").value("Created vault"));
    }
}
