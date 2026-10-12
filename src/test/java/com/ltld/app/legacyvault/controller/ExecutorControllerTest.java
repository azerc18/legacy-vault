package com.ltld.app.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.dto.executordto.AssignedVaultResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidAttachmentException;
import com.ltld.app.legacyvault.exception.InvalidDeathCertificateException;
import com.ltld.app.legacyvault.exception.VerificationNotFoundException;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.LegalVerificationSubmissionService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @MockitoBean
    private LegalVerificationSubmissionService legalVerificationSubmissionService;

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
    @DisplayName("GET /api/executors/assigned-vaults: trả về 200 và danh sách Vault được ủy quyền")
    void getAssignedVaults_returns200() throws Exception {
        UUID vaultId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        AssignedVaultResponse item = AssignedVaultResponse.builder()
                .vaultId(vaultId)
                .vaultName("Family Assets")
                .description("Vault bảo mật gia đình")
                .vaultStatus(VaultStatus.ARCHIVED_LOCKED)
                .ownerId(ownerId)
                .ownerName("Owner Test")
                .assetCount(2L)
                .assetTypes(List.of(AssetType.BANK_ACCOUNT, AssetType.DOCUMENT))
                .beneficiaryEmailMasked("b***@example.com")
                .beneficiaryNameMasked("N*** V*** A***")
                .executorType(ExecutorType.PRIMARY)
                .activatedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(executorHandoverService.getAssignedVaults(eq(userEmail))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/executors/assigned-vaults")
                        .principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Assigned vaults retrieved successfully"))
                .andExpect(jsonPath("$.data[0].vaultId").value(vaultId.toString()))
                .andExpect(jsonPath("$.data[0].vaultName").value("Family Assets"))
                .andExpect(jsonPath("$.data[0].vaultStatus").value("ARCHIVED_LOCKED"))
                .andExpect(jsonPath("$.data[0].ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data[0].assetCount").value(2))
                .andExpect(jsonPath("$.data[0].assetTypes[0]").value("BANK_ACCOUNT"))
                .andExpect(jsonPath("$.data[0].assetTypes[1]").value("DOCUMENT"))
                .andExpect(jsonPath("$.data[0].beneficiaryEmailMasked").value("b***@example.com"))
                .andExpect(jsonPath("$.data[0].executorType").value("PRIMARY"));
    }

    @Test
    @DisplayName("GET /api/executors/assigned-vaults: không có assignment trả về 404")
    void getAssignedVaults_notFound_returns404() throws Exception {
        Principal mockPrincipal = () -> "executor@example.com";

        when(executorHandoverService.getAssignedVaults(any()))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        mockMvc.perform(get("/api/executors/assigned-vaults")
                        .principal(mockPrincipal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Executor assignment not found."));
    }

    @Test
    @DisplayName("GET /api/executors/assigned-vaults: Executor chưa kích hoạt trả về 403")
    void getAssignedVaults_notActive_returns403() throws Exception {
        Principal mockPrincipal = () -> "executor@example.com";

        when(executorHandoverService.getAssignedVaults(any()))
                .thenThrow(new ExecutorNotActiveException("Access denied. Executor has not been activated yet."));

        mockMvc.perform(get("/api/executors/assigned-vaults")
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

    private static MockMultipartFile deathCertificate() {
        return new MockMultipartFile("deathCertificate", "giay-chung-tu.pdf",
                MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 test".getBytes());
    }

    @Test
    @DisplayName("POST /api/executors/legal-verification-requests: gửi yêu cầu thành công trả về 201")
    void submitLegalVerificationRequest_success_returns201() throws Exception {
        UUID vault1 = UUID.randomUUID();
        UUID vault2 = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String userEmail = "executor@example.com";
        Principal mockPrincipal = () -> userEmail;

        VerificationRequestResponseDto response = VerificationRequestResponseDto.builder()
                .id(requestId)
                .ownerName("Owner Test")
                .executorName("Executor Test")
                .status(LegalVerificationStatus.PENDING)
                .hasDeathCertificate(true)
                .vaultIds(List.of(vault1, vault2))
                .build();

        when(legalVerificationSubmissionService.submit(any(), eq(userEmail))).thenReturn(response);

        mockMvc.perform(multipart("/api/executors/legal-verification-requests")
                        .file(deathCertificate())
                        .param("vaultIds", vault1.toString(), vault2.toString())
                        .param("note", "Gửi kèm giấy chứng tử bản scan")
                        .principal(mockPrincipal))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã gửi yêu cầu xác minh pháp lý"))
                .andExpect(jsonPath("$.data.id").value(requestId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.vaultIds[1]").value(vault2.toString()));

        verify(legalVerificationSubmissionService).submit(argThat(r ->
                r.getVaultIds().equals(List.of(vault1, vault2))
                        && "giay-chung-tu.pdf".equals(r.getDeathCertificate().getOriginalFilename())
                        && "Gửi kèm giấy chứng tử bản scan".equals(r.getNote())), eq(userEmail));
    }

    @Test
    @DisplayName("POST /api/executors/legal-verification-requests: thiếu danh sách Vault trả về 400")
    void submitLegalVerificationRequest_missingVaultIds_returns400() throws Exception {
        mockMvc.perform(multipart("/api/executors/legal-verification-requests")
                        .file(deathCertificate())
                        .principal(() -> "executor@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.vaultIds").value("Vui lòng chọn ít nhất một Vault cần mở khóa"));

        verify(legalVerificationSubmissionService, never()).submit(any(), any());
    }

    @Test
    @DisplayName("POST /api/executors/legal-verification-requests: thiếu tệp giấy chứng tử trả về 400")
    void submitLegalVerificationRequest_missingFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/executors/legal-verification-requests")
                        .param("vaultIds", UUID.randomUUID().toString())
                        .principal(() -> "executor@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.deathCertificate").value("Vui lòng tải lên giấy chứng tử"));

        verify(legalVerificationSubmissionService, never()).submit(any(), any());
    }

    @Test
    @DisplayName("POST /api/executors/legal-verification-requests: tệp sai định dạng trả về 400")
    void submitLegalVerificationRequest_invalidFile_returns400() throws Exception {
        when(legalVerificationSubmissionService.submit(any(), any()))
                .thenThrow(new InvalidDeathCertificateException(
                        "Định dạng tệp không hợp lệ. Chỉ chấp nhận .pdf, .jpg, .png."));

        mockMvc.perform(multipart("/api/executors/legal-verification-requests")
                        .file(new MockMultipartFile("deathCertificate", "scan.docx",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, "abc".getBytes()))
                        .param("vaultIds", UUID.randomUUID().toString())
                        .principal(() -> "executor@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Định dạng tệp không hợp lệ. Chỉ chấp nhận .pdf, .jpg, .png."));
    }

    @Test
    @DisplayName("POST /api/executors/legal-verification-requests: Vault đã có yêu cầu đang chờ trả về 409")
    void submitLegalVerificationRequest_duplicate_returns409() throws Exception {
        when(legalVerificationSubmissionService.submit(any(), any()))
                .thenThrow(new DuplicateVerificationRequestException(
                        "Đã tồn tại yêu cầu xác minh pháp lý đang chờ xử lý cho Vault được chọn."));

        mockMvc.perform(multipart("/api/executors/legal-verification-requests")
                        .file(deathCertificate())
                        .param("vaultIds", UUID.randomUUID().toString())
                        .principal(() -> "executor@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
