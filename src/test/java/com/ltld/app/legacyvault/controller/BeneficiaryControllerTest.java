package com.ltld.app.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.VerificationStatus;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(BeneficiaryController.class)
public class BeneficiaryControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private BeneficiaryService beneficiaryService;

    private BeneficiaryClaimRequest validRequest() {
        BeneficiaryClaimRequest request = new BeneficiaryClaimRequest();
        request.setVaultId(UUID.randomUUID());
        request.setVerificationMethod(VerificationMethod.OTP);
        return request;
    }

    @Test
    void initializeClaim_success_returns200AndFullResponse() throws Exception {

        UUID userId = UUID.randomUUID();
        Principal principal = () -> userId.toString();

        BeneficiaryClaimRequest request = validRequest();
        UUID mockVerificationId = UUID.randomUUID();

        BeneficiaryClaimResponse mockResponse = BeneficiaryClaimResponse.builder()
                .claimId(UUID.randomUUID())
                .vaultId(request.getVaultId())
                .verificationId(mockVerificationId)
                .build();

        when(beneficiaryService.initializeClaim(any(), eq(userId))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/beneficiaries/claim")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.verificationId").value(mockVerificationId.toString()));

        verify(beneficiaryService).initializeClaim(any(), eq(userId));
    }

    @Test
    void initializeClaim_serviceThrowsBeneficiaryException_returnsCorrectError() throws Exception {

        UUID userId = UUID.randomUUID();
        Principal principal = () -> userId.toString();

        BeneficiaryClaimRequest request = validRequest();

        // Giả lập Service ném lỗi 404 Không tìm thấy (Sai người gọi)
        when(beneficiaryService.initializeClaim(any(), eq(userId)))
                .thenThrow(new BeneficiaryException("Không tìm thấy yêu cầu nhận tài sản.", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/beneficiaries/claim")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()) // Expect mã HTTP 404
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy yêu cầu nhận tài sản."));
    }

    @Test
    void initializeClaim_missingVaultId_returns400() throws Exception {
        mockMvc.perform(post("/api/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationMethod\":\"OTP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initializeClaim_missingVerificationMethod_returns400() throws Exception {
        mockMvc.perform(post("/api/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaultId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===================== FR-17 =====================

    private VerifyIdentityRequest validVerifyRequest() {
        VerifyIdentityRequest request = new VerifyIdentityRequest();
        request.setVaultId(UUID.randomUUID());
        request.setVerificationMethod(VerificationMethod.OTP);
        request.setOtp("123456");
        return request;
    }

    @Test
    void sendIdentityOtp_success_passesPrincipalIdToService() throws Exception {
        UUID userId = UUID.randomUUID();
        Principal principal = () -> userId.toString();

        mockMvc.perform(post("/api/beneficiaries/verifications/otp")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP đã được gửi đến email của bạn."));

        verify(beneficiaryService).sendIdentityOtp(any(), eq(userId));
    }

    @Test
    void sendIdentityOtp_missingVaultId_returns400() throws Exception {
        Principal principal = () -> UUID.randomUUID().toString();

        mockMvc.perform(post("/api/beneficiaries/verifications/otp")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationMethod\":\"OTP\"}"))
                .andExpect(status().isBadRequest());

        verify(beneficiaryService, never()).sendIdentityOtp(any(), any());
    }

    @Test
    void sendIdentityOtp_resendTooSoon_returns429() throws Exception {
        Principal principal = () -> UUID.randomUUID().toString();
        doThrow(new BeneficiaryException("Vui lòng đợi một lúc trước khi yêu cầu gửi lại OTP.",
                HttpStatus.TOO_MANY_REQUESTS))
                .when(beneficiaryService).sendIdentityOtp(any(), any());

        mockMvc.perform(post("/api/beneficiaries/verifications/otp")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void verifyIdentity_success_returnsSessionInfo() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID verificationId = UUID.randomUUID();
        Principal principal = () -> userId.toString();

        VerifyIdentityResponse response = VerifyIdentityResponse.builder()
                .verificationId(verificationId)
                .status(VerificationStatus.SUCCESS)
                .verifiedAt(LocalDateTime.now())
                .viewSessionExpiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(beneficiaryService.verifyIdentity(any(), eq(userId))).thenReturn(response);

        mockMvc.perform(post("/api/beneficiaries/verifications/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validVerifyRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.verificationId").value(verificationId.toString()));
    }

    @Test
    void verifyIdentity_otpNotSixDigits_returns400() throws Exception {
        Principal principal = () -> UUID.randomUUID().toString();

        mockMvc.perform(post("/api/beneficiaries/verifications/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaultId\":\"" + UUID.randomUUID()
                                + "\",\"verificationMethod\":\"OTP\",\"otp\":\"12ab\"}"))
                .andExpect(status().isBadRequest());

        verify(beneficiaryService, never()).verifyIdentity(any(), any());
    }

    @Test
    void verifyIdentity_locked_returns423() throws Exception {
        Principal principal = () -> UUID.randomUUID().toString();
        when(beneficiaryService.verifyIdentity(any(), any()))
                .thenThrow(new BeneficiaryException("Truy cập đã bị tạm khóa.", HttpStatus.LOCKED));

        mockMvc.perform(post("/api/beneficiaries/verifications/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validVerifyRequest())))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Truy cập đã bị tạm khóa."));
    }

    @Test
    void getInheritedAssets_success_returnsSummariesWithoutSecrets() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        Principal principal = () -> userId.toString();

        when(beneficiaryService.getInheritedAssets(vaultId, userId)).thenReturn(List.of(
                InheritedAssetSummaryResponse.builder()
                        .id(UUID.randomUUID())
                        .assetType(AssetType.BANK_ACCOUNT)
                        .assetName("Vietcombank")
                        .build()));

        mockMvc.perform(get("/api/beneficiaries/vaults/{vaultId}/assets", vaultId)
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetName").value("Vietcombank"))
                .andExpect(jsonPath("$.data[0].assetType").value("BANK_ACCOUNT"))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());
    }

    @Test
    void getInheritedAssets_noViewSession_returns403() throws Exception {
        Principal principal = () -> UUID.randomUUID().toString();
        when(beneficiaryService.getInheritedAssets(any(), any()))
                .thenThrow(new BeneficiaryException("Cần xác thực danh tính trước khi xem tài sản.",
                        HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/beneficiaries/vaults/{vaultId}/assets", UUID.randomUUID())
                        .principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cần xác thực danh tính trước khi xem tài sản."));
    }

    @Test
    void getInheritedAssetDetail_success_returnsDecryptedDataWithNoStoreHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Principal principal = () -> userId.toString();

        when(beneficiaryService.getInheritedAssetDetail(vaultId, assetId, userId)).thenReturn(
                InheritedAssetDetailResponse.builder()
                        .id(assetId)
                        .assetType(AssetType.CRYPTO_WALLET)
                        .assetName("Ví Binance")
                        .secret("my-secret")
                        .notes("my-notes")
                        .build());

        mockMvc.perform(get("/api/beneficiaries/vaults/{vaultId}/assets/{assetId}", vaultId, assetId)
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.secret").value("my-secret"))
                .andExpect(jsonPath("$.data.notes").value("my-notes"));
    }

    @Test
    void getInheritedAssetDetail_assetNotFound_returns404() throws Exception {
        Principal principal = () -> UUID.randomUUID().toString();
        when(beneficiaryService.getInheritedAssetDetail(any(), any(), any()))
                .thenThrow(new BeneficiaryException("Không tìm thấy tài sản.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/beneficiaries/vaults/{vaultId}/assets/{assetId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

}