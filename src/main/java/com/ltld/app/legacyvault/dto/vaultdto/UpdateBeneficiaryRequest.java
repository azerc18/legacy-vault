package com.ltld.app.legacyvault.dto.vaultdto;

import jakarta.validation.constraints.Size;
import lombok.Data;

// Owner sửa tên / mối quan hệ của một người thụ hưởng. Email không sửa ở đây (muốn đổi email thì gỡ rồi thêm lại).
@Data
public class UpdateBeneficiaryRequest {
    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    @Size(max = 50, message = "Relationship must be at most 50 characters")
    private String relationship;
}
