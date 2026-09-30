package com.ltld.app.legacyvault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        BeneficiaryClaimRequest request = validRequest();
        UUID mockVerificationId = UUID.randomUUID();

        BeneficiaryClaimResponse mockResponse = BeneficiaryClaimResponse.builder()
                .claimId(UUID.randomUUID())
                .vaultId(request.getVaultId())
                .verificationId(mockVerificationId)
                .build();

        when(beneficiaryService.initializeClaim(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Success")) // Assert Message như yêu cầu của Reviewer
                .andExpect(jsonPath("$.data.verificationId").value(mockVerificationId.toString()));
    }

    @Test
    void initializeClaim_serviceThrowsBeneficiaryException_returnsCorrectError() throws Exception {
        BeneficiaryClaimRequest request = validRequest();

        // Giả lập Service ném lỗi 404 Không tìm thấy (Sai người gọi)
        when(beneficiaryService.initializeClaim(any(), any()))
                .thenThrow(new BeneficiaryException("Không tìm thấy yêu cầu nhận tài sản.", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()) // Expect mã HTTP 404
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy yêu cầu nhận tài sản."));
    }
}