package com.ltld.app.legacyvault.dto.identitydto;

import com.ltld.app.legacyvault.enums.KycSource;
import com.ltld.app.legacyvault.enums.VnProvince;
import com.ltld.app.legacyvault.utility.CitizenId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SubmitIdentityRequest {
    @NotBlank(message = "Citizen ID is required")
    @Size(max = 20, message = "Citizen ID is too long")
    private String citizenId;

    // CMND 9 số cũ (nếu có, đọc từ QR); giúp đối chiếu giấy chứng tử ghi số CMND
    @Size(max = 20, message = "Old ID number is too long")
    private String oldIdNumber;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    private String fullName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private CitizenId.Gender gender;

    @NotNull(message = "Province is required")
    private VnProvince residenceProvince;

    @NotNull(message = "Source is required")
    private KycSource source;

    // Đồng ý điều khoản xử lý dữ liệu cá nhân (bắt buộc)
    private boolean dataConsent;

    // Đồng ý gửi ảnh CCCD cho AI đọc (bắt buộc khi source = OCR)
    private boolean aiOcrConsent;

    // Họ tên CCCD khác họ tên tài khoản: true để cập nhật họ tên tài khoản theo CCCD
    private boolean updateAccountName;
}
