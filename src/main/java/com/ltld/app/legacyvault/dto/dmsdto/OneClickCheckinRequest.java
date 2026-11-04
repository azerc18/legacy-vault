package com.ltld.app.legacyvault.dto.dmsdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** F2: body của POST /api/dms/checkin-link, gửi từ trang FE /dms/checkin khi Owner bấm nút. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OneClickCheckinRequest {
    @NotBlank(message = "Check-in token is required")
    private String token;
}
