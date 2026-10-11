package com.ltld.app.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidAttachmentException;
import com.ltld.app.legacyvault.exception.VerificationNotFoundException;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutorController.class)
public class ExecutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private DmsAutomationService dmsAutomationService;

    @MockitoBean
    private ExecutorHandoverService executorHandoverService;

    @MockitoBean
    private VerificationExchangeService verificationExchangeService;

    @Test
    @DisplayName("GET /api/executors/{executorId}/handover-progress: trả về 200 và danh sách tiến độ")
    void getHandoverProgressByAssignment_returns200() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        VaultHandoverProgressResponse item = VaultHandoverProgressResponse.builder()
                .vaultId(vaultId)
                .vaultName("Family Assets")
                .description("Vault bảo mật gia đình")
                .handoverStatus(HandoverStatus.PENDING)
                .assetCount(2L)
                .claimDeadlineAt(LocalDateTime.now().plusDays(30))
                .beneficiaryEmailMasked("b***@example.com")
                .beneficiaryNameMasked("N*** V*** A***")
                .build();

        when(executorHandoverService.getHandoverProgress(eq(assignmentId), eq(userEmail)))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/executors/{executorId}/handover-progress", assignmentId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vault handover progress retrieved successfully"))
                .andExpect(jsonPath("$.data[0].vaultId").value(vaultId.toString()))
                .andExpect(jsonPath("$.data[0].vaultName").value("Family Assets"))
                .andExpect(jsonPath("$.data[0].handoverStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].assetCount").value(2))
                .andExpect(jsonPath("$.data[0].beneficiaryEmailMasked").value("b***@example.com"))
                .andExpect(jsonPath("$.data[0].beneficiaryNameMasked").value("N*** V*** A***"));
    }

    @Test
    @DisplayName("GET /api/executors/handover-progress: gọi theo phiên đăng nhập trả về 200")
    void getHandoverProgress_byLogin_returns200() throws Exception {
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        when(executorHandoverService.getHandoverProgress(eq(userEmail)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/executors/handover-progress")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/executors/{executorId}/handover-progress: không tìm thấy assignment trả về 404")
    void getHandoverProgress_notFound_returns404() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        Principal mockPrincipal = () -> "executor@example.com";

        when(executorHandoverService.getHandoverProgress(eq(assignmentId), any()))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        mockMvc.perform(get("/api/executors/{executorId}/handover-progress", assignmentId)
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Executor assignment not found."));
    }

    @Test
    @DisplayName("GET /api/executors/{executorId}/handover-progress: Executor chưa kích hoạt trả về 403")
    void getHandoverProgress_notActive_returns403() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        Principal mockPrincipal = () -> "executor@example.com";

        when(executorHandoverService.getHandoverProgress(eq(assignmentId), any()))
                .thenThrow(new ExecutorNotActiveException("Access denied. Executor has not been activated yet."));

        mockMvc.perform(get("/api/executors/{executorId}/handover-progress", assignmentId)
                        .principal(mockPrincipal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied. Executor has not been activated yet."));
    }

    @Test
    @DisplayName("POST /api/executors/{executorId}/acknowledge: xác nhận kích hoạt thành công trả về 200")
    void acknowledge_returns200() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        ExecutorActivationResponse response = ExecutorActivationResponse.builder()
                .id(assignmentId)
                .executorType(ExecutorType.PRIMARY)
                .status(ExecutorStatus.ACTIVE)
                .activatedAt(LocalDateTime.now().minusHours(1))
                .acknowledgedAt(LocalDateTime.now())
                .build();

        when(dmsAutomationService.acknowledgeActivation(eq(assignmentId), eq(userEmail)))
                .thenReturn(response);

        mockMvc.perform(post("/api/executors/{executorId}/acknowledge", assignmentId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/executors/verifications/{verificationId}/messages: Gửi tin nhắn thành công trả về 200")
    void sendVerificationMessage_success_returns200() throws Exception {
        UUID verificationId = UUID.randomUUID();
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder()
                .messageContent("Gửi bổ sung giấy tờ")
                .attachmentUrl("https://example.com/doc.pdf")
                .attachmentName("doc.pdf")
                .attachmentSize(1024L)
                .build();

        VerificationMessageResponse response = VerificationMessageResponse.builder()
                .id(UUID.randomUUID())
                .verificationId(verificationId)
                .senderRole(ExchangeSenderRole.EXECUTOR)
                .messageContent("Gửi bổ sung giấy tờ")
                .attachmentName("doc.pdf")
                .build();

        when(verificationExchangeService.sendExecutorMessage(eq(verificationId), any(), eq(userEmail)))
                .thenReturn(response);

        mockMvc.perform(post("/api/executors/verifications/{verificationId}/messages", verificationId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification message sent successfully"))
                .andExpect(jsonPath("$.data.messageContent").value("Gửi bổ sung giấy tờ"))
                .andExpect(jsonPath("$.data.attachmentName").value("doc.pdf"));
    }

    @Test
    @DisplayName("POST /api/executors/verifications/{verificationId}/messages: Tệp đính kèm không hợp lệ trả về 400")
    void sendVerificationMessage_invalidAttachment_returns400() throws Exception {
        UUID verificationId = UUID.randomUUID();
        Principal mockPrincipal = () -> "executor@example.com";

        SendVerificationMessageRequest request = SendVerificationMessageRequest.builder().build();

        when(verificationExchangeService.sendExecutorMessage(eq(verificationId), any(), any()))
                .thenThrow(new InvalidAttachmentException("Nội dung tin nhắn hoặc tệp đính kèm không được để trống."));

        mockMvc.perform(post("/api/executors/verifications/{verificationId}/messages", verificationId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Nội dung tin nhắn hoặc tệp đính kèm không được để trống."));
    }

    @Test
    @DisplayName("GET /api/executors/verifications/{verificationId}/messages: Lấy lịch sử tin nhắn thành công trả về 200")
    void getVerificationMessages_success_returns200() throws Exception {
        UUID verificationId = UUID.randomUUID();
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        when(verificationExchangeService.getExchangeMessages(eq(verificationId), eq(userEmail)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/executors/verifications/{verificationId}/messages", verificationId)
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification messages retrieved successfully"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/executors/verifications/{verificationId}/messages: Không tìm thấy verification trả về 404")
    void getVerificationMessages_notFound_returns404() throws Exception {
        UUID verificationId = UUID.randomUUID();
        Principal mockPrincipal = () -> "executor@example.com";

        when(verificationExchangeService.getExchangeMessages(eq(verificationId), any()))
                .thenThrow(new VerificationNotFoundException("Identity verification request not found."));

        mockMvc.perform(get("/api/executors/verifications/{verificationId}/messages", verificationId)
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Identity verification request not found."));
    }
}
