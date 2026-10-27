package com.ltld.app.legacyvault.controller.owner;

import com.ltld.app.legacyvault.controller.OwnerExecutorController;
import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.service.executorservice.ExecutorManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OwnerExecutorController.class)
class OwnerExecutorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExecutorManagementService executorManagementService;

    private final UUID ownerId = UUID.randomUUID();
    private final Principal principal = () -> ownerId.toString();

    @Test
    void list_returns200WithAllPositions() throws Exception {
        when(executorManagementService.listExecutors(ownerId)).thenReturn(List.of(
                ExecutorAssignmentResponse.builder().executorType(ExecutorType.PRIMARY)
                        .status(ExecutorStatus.INACTIVE).email("p@example.test").build(),
                ExecutorAssignmentResponse.builder().executorType(ExecutorType.BACKUP)
                        .status(ExecutorStatus.REVOKED).email("b@example.test").build()));

        mockMvc.perform(get("/api/owner/executors").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].executorType").value("PRIMARY"))
                .andExpect(jsonPath("$.data[1].executorType").value("BACKUP"))
                .andExpect(jsonPath("$.data[1].status").value("REVOKED"));
    }

    @Test
    void list_empty_returns200WithEmptyArray() throws Exception {
        when(executorManagementService.listExecutors(ownerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/owner/executors").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void assign_lowercaseType_returns200WithAssignment() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        when(executorManagementService.assignExecutor(eq(ownerId), eq(ExecutorType.PRIMARY), any()))
                .thenReturn(ExecutorAssignmentResponse.builder()
                        .assignmentId(assignmentId)
                        .executorType(ExecutorType.PRIMARY)
                        .status(ExecutorStatus.INACTIVE)
                        .email("exec@example.test")
                        .fullName("Exec")
                        .build());

        mockMvc.perform(put("/api/owner/executors/{type}", "primary")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"exec@example.test\",\"confirmReplace\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.executorType").value("PRIMARY"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.email").value("exec@example.test"));

        ArgumentCaptor<AssignExecutorRequest> captor = ArgumentCaptor.forClass(AssignExecutorRequest.class);
        verify(executorManagementService).assignExecutor(eq(ownerId), eq(ExecutorType.PRIMARY), captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("exec@example.test");
        assertThat(captor.getValue().isConfirmReplace()).isTrue();
    }

    @Test
    void assign_invalidEmail_returns400() throws Exception {
        mockMvc.perform(put("/api/owner/executors/{type}", "BACKUP")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").exists());
        verify(executorManagementService, never()).assignExecutor(any(), any(), any());
    }

    @Test
    void assign_blankEmail_returns400() throws Exception {
        mockMvc.perform(put("/api/owner/executors/{type}", "BACKUP")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(executorManagementService, never()).assignExecutor(any(), any(), any());
    }

    @Test
    void assign_emailTooLong_returns400() throws Exception {
        // Đúng cú pháp email (local 64 ký tự, các nhãn domain 63 ký tự) nhưng dài 260 ký tự: chỉ vi phạm @Size(max = 255).
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";

        mockMvc.perform(put("/api/owner/executors/{type}", "BACKUP")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").value("Email must be at most 255 characters"));
        verify(executorManagementService, never()).assignExecutor(any(), any(), any());
    }

    @Test
    void assign_unknownType_returns400() throws Exception {
        mockMvc.perform(put("/api/owner/executors/{type}", "TERTIARY")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"exec@example.test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verify(executorManagementService, never()).assignExecutor(any(), any(), any());
    }

    @Test
    void assign_serviceConflict_returns409WithMessage() throws Exception {
        when(executorManagementService.assignExecutor(any(), any(), any()))
                .thenThrow(new ExecutorManagementException("Cần confirmReplace", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/owner/executors/{type}", "PRIMARY")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"exec@example.test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cần confirmReplace"));
    }

    @Test
    void assign_serviceUnprocessable_returns422() throws Exception {
        when(executorManagementService.assignExecutor(any(), any(), any()))
                .thenThrow(new ExecutorManagementException("Chưa xác minh", HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(put("/api/owner/executors/{type}", "PRIMARY")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"exec@example.test\"}"))
                .andExpect(status().is(422));
    }

    @Test
    void revoke_returns200() throws Exception {
        when(executorManagementService.revokeExecutor(ownerId, ExecutorType.BACKUP))
                .thenReturn(ExecutorAssignmentResponse.builder()
                        .executorType(ExecutorType.BACKUP).status(ExecutorStatus.REVOKED).build());

        mockMvc.perform(delete("/api/owner/executors/{type}", "backup").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void revoke_notFound_returns404() throws Exception {
        when(executorManagementService.revokeExecutor(any(), any()))
                .thenThrow(new ExecutorManagementException("Không có Executor", HttpStatus.NOT_FOUND));

        mockMvc.perform(delete("/api/owner/executors/{type}", "PRIMARY").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không có Executor"));
    }
}
