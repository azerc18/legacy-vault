package com.ltld.app.legacyvault.dto.beneficiarydto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CloseVaultRequest {
    // Xác nhận lần cuối của Beneficiary (SRS FR-19 bước 3-4). false thì không đóng hồ sơ.
    @NotNull(message = "Cần xác nhận để đóng hồ sơ")
    private Boolean confirmed;
    // Beneficiary xác nhận rõ ràng việc đóng hồ sơ khi còn tài sản chưa xem (không hoàn tác được)
    private Boolean acknowledgeUnviewed;
}