package com.ltld.app.legacyvault.dto.beneficiarydto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CloseVaultRequest {
    // Xác nhận lần cuối của Beneficiary (SRS FR-19 bước 3-4). false thì không đóng hồ sơ.
    @NotNull(message = "Confirmation is required to close the case")
    private Boolean confirmed;
    // Beneficiary xác nhận rõ ràng việc đóng hồ sơ khi còn tài sản chưa xem (không hoàn tác được)
    private Boolean acknowledgeUnviewed;
}