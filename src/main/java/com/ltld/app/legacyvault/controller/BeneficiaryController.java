package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    // FR-16: Khởi tạo yêu cầu nhận tài sản
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<BeneficiaryClaimResponse>> initializeClaim(
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        // TODO(#4): thay bằng user từ SecurityContext khi có JWT. Hiện endpoint chưa dùng được end-to-end.
        // Tạm thời hard-code UUID (không khớp beneficiary nào nên API luôn trả 404, fail-closed).
        UUID currentUserId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        BeneficiaryClaimResponse response = beneficiaryService.initializeClaim(request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}