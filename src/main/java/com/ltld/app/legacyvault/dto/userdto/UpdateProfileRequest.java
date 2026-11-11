package com.ltld.app.legacyvault.dto.userdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    private String fullName;

    // Ở đây chỉ chặn ký tự lạ (số cũ đã lưu đều thoả điều kiện này). Quy tắc số di động Việt Nam kiểm ở service
    // (VnPhoneNumber) và chỉ khi số thay đổi, để số cũ không đúng chuẩn không chặn việc sửa tên.
    @Pattern(regexp = "^$|^[0-9+()\\-.\\s]{1,30}$", message = "Invalid phone number")
    private String phoneNumber;

    // Ngôn ngữ nhận email: "vi" hoặc "en". Không gửi thì giữ nguyên.
    @Pattern(regexp = "^(vi|en)$", message = "Language must be vi or en")
    private String preferredLanguage;
}
