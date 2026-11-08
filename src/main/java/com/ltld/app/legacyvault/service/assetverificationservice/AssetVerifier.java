package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * C.5: mỗi loại tài sản một verifier (@Component). Thêm loại/cách mới = thêm một class, không sửa service.
 * Quy ước: KHÔNG bao giờ thu thập mật khẩu/seed phrase của dịch vụ khác để xác thực.
 */
public interface AssetVerifier {

    AssetType type();

    /** Các cách xác thực loại này hỗ trợ (FE vẽ màn hình từ danh sách này). */
    List<AssetVerificationMethod> methods();

    /** Bắt đầu: gửi mã, tạo thông điệp thử thách... Chưa xong thì trả pending. */
    StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId);

    /** Hoàn tất: payload là dữ liệu Owner gửi lên (mã OTP, chữ ký, hash...). */
    VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                         Map<String, String> payload, UUID ownerId);

    /** Kết quả bước start. challengeHash = hash của mã/thông điệp thử thách (không lưu mã thô). */
    record StartResult(VerificationProvider provider, String message, String challengeHash,
                       Map<String, String> clientData, LocalDateTime challengeExpiresAt) {
    }

    /** Kết quả bước confirm. evidence chỉ chứa dữ liệu không nhạy cảm. */
    record VerifyResult(boolean success, VerificationProvider provider, String message,
                        Map<String, String> evidence) {
    }
}
