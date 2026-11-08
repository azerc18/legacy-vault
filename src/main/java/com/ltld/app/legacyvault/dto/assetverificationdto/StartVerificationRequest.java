package com.ltld.app.legacyvault.dto.assetverificationdto;

import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartVerificationRequest {
    @NotNull
    private AssetVerificationMethod method;
}
