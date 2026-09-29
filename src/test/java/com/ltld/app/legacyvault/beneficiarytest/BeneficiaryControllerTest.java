package com.ltld.app.legacyvault.beneficiarytest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.controller.BeneficiaryController;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Dùng @MockitoBean để đồng bộ với cấu trúc Spring Boot của nhóm
    @MockitoBean
    private BeneficiaryService beneficiaryService;

    private BeneficiaryClaimRequest validRequest() {
        BeneficiaryClaimRequest request = new BeneficiaryClaimRequest();
        request.setVaultId(UUID.randomUUID());
        request.setVerificationMethod(VerificationMethod.OTP);
        return request;
    }

    @Test
    void initializeClaim_success_returnsOk() throws Exception {
        BeneficiaryClaimRequest request = validRequest();

        BeneficiaryClaimResponse mockResponse = BeneficiaryClaimResponse.builder()
                .claimId(UUID.randomUUID())
                .vaultId(request.getVaultId())
                .build();

        when(beneficiaryService.initializeClaim(any(BeneficiaryClaimRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vaultId").value(request.getVaultId().toString()));

        verify(beneficiaryService, times(1)).initializeClaim(any(BeneficiaryClaimRequest.class));
    }

    @Test
    void initializeClaim_missingVaultId_returnsBadRequest() throws Exception {
        BeneficiaryClaimRequest request = validRequest();
        request.setVaultId(null); // Gây lỗi Validation

        mockMvc.perform(post("/api/v1/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Đảm bảo Service không bao giờ được gọi nếu dữ liệu đầu vào sai
        verify(beneficiaryService, never()).initializeClaim(any());
    }

    @Test
    void initializeClaim_missingVerificationMethod_returnsBadRequest() throws Exception {
        BeneficiaryClaimRequest request = validRequest();
        request.setVerificationMethod(null); // Gây lỗi Validation

        mockMvc.perform(post("/api/v1/beneficiaries/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(beneficiaryService, never()).initializeClaim(any());
    }
}