package com.ltld.app.legacyvault.dto.dmsdto;

import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * X2 (C1/M1): người xác nhận chéo bấm nút trên trang FE /dms/confirm. Quyết định chỉ đến từ body này,
 * token trong link email không mang quyết định.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsConfirmDecisionRequest {

    @NotBlank(message = "Confirmation token is required.")
    private String token;

    @NotNull(message = "Confirmation decision (ALIVE or UNREACHABLE) is required.")
    private DmsConfirmerDecision decision;
}
