package com.ltld.app.legacyvault.dto.beneficiarydto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// N2b: lý do từ chối (tùy chọn)
@Getter
@Setter
@NoArgsConstructor
public class DeclineClaimRequest {
    @Size(max = 500)
    private String reason;
}