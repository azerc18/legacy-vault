package com.ltld.app.legacyvault.dto.identitydto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** Thông tin đọc được từ QR / ảnh CCCD để FE điền sẵn; không lưu gì ở bước này. */
@Getter
@Builder
public class IdentityExtractResponse {
    private String source;
    private String citizenId;
    private String oldIdNumber;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    // Tỉnh (34 tỉnh mới) suy ra từ địa chỉ thường trú; null nếu không suy ra được
    private String residenceProvince;
    private List<String> warnings;
}
