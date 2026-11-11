package com.ltld.app.legacyvault.service.identityservice;

import com.ltld.app.legacyvault.dto.identitydto.*;
import com.ltld.app.legacyvault.enums.OwnerIdMatch;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Xác minh danh tính Owner (KYC BASIC) trước khi bật Dead Man's Switch. */
public interface OwnerIdentityService {

    IdentityStatusResponse getStatus(UUID userId);

    /** Đọc chuỗi QR CCCD; không lưu gì. */
    IdentityExtractResponse extractFromQr(UUID userId, String qrText);

    /** Đọc ảnh CCCD bằng AI; cần Owner đồng ý gửi ảnh cho AI. Không lưu ảnh. */
    IdentityExtractResponse extractFromImage(UUID userId, MultipartFile image, boolean aiOcrConsent);

    PhoneOtpResponse sendPhoneOtp(UUID userId, String rawPhone);

    IdentityStatusResponse verifyPhoneOtp(UUID userId, String code);

    IdentityStatusResponse submit(UUID userId, SubmitIdentityRequest request);

    /** Đối chiếu số giấy tờ đọc từ giấy chứng tử với CCCD / CMND Owner đã xác minh. */
    OwnerIdMatch matchDocumentId(UUID ownerId, String idNumberOnDocument);
}
