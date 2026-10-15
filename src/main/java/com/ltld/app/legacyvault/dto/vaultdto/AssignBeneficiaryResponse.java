package com.ltld.app.legacyvault.dto.vaultdto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AssignBeneficiaryResponse {
    private UUID vaultId;
    private String beneficiaryEmail;
    // NULL khi người thụ hưởng chưa có tài khoản đã xác minh
    private String beneficiaryName;
    // true: đã gửi lời mời đăng ký, sẽ liên kết khi email được xác minh
    private boolean pendingRegistration;
    // true: đã ghi đè người thụ hưởng trước đó
    private boolean replaced;
}
