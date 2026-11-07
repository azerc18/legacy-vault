package com.ltld.app.legacyvault.dto.assetverificationdto;

import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmVerificationRequest {
    @NotNull
    private AssetVerificationMethod method;

    /** Dữ liệu theo từng cách: {"code":"123456"}, {"signature":"0x..."}, {"sha256":"..."}... */
    private Map<String, String> payload;
}