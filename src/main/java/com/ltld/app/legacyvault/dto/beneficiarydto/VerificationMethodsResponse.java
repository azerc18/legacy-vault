package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.VerificationMethod;
import lombok.Builder;
import lombok.Data;

import java.util.List;

// FR-17: các phương thức xác thực danh tính đang bật, để FE chỉ hiện lựa chọn dùng được
@Data
@Builder
public class VerificationMethodsResponse {
    private List<VerificationMethod> methods;
}
