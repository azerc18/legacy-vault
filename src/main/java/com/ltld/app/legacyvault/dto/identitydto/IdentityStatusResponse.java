package com.ltld.app.legacyvault.dto.identitydto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class IdentityStatusResponse {
    // NONE / BASIC / VERIFIED
    private String kycLevel;
    private boolean verified;
    // QR / OCR / MANUAL; null khi chưa xác minh
    private String source;
    private String citizenIdMasked;
    private boolean hasOldIdNumber;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String residenceProvince;
    private Instant verifiedAt;
    // Họ tên trên tài khoản, để FE báo trước khi lệch với CCCD
    private String accountFullName;
    // Số điện thoại hiện tại dạng hiển thị (0912 345 678); null nếu chưa có hoặc số cũ không đúng chuẩn
    private String phoneNumber;
    private boolean phoneVerified;
    // DMS đã kích hoạt / đang xác nhận chéo: không được sửa thông tin danh tính
    private boolean locked;
    private List<ProvinceOption> provinces;

    public record ProvinceOption(String code, String name) {
    }
}
