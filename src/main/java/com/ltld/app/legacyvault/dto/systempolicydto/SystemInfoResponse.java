package com.ltld.app.legacyvault.dto.systempolicydto;

/**
 * Thông tin chỉ đọc cho Admin: mã hoá, lưu trữ tệp, sao lưu, phiên bản CSDL.
 * KHÔNG chứa khoá, mật khẩu hay endpoint; chỉ cho biết đã cấu hình hay chưa.
 */
public record SystemInfoResponse(Crypto crypto, Storage storage, Backup backup, String schemaVersion) {

    /**
     * @param otpKeySeparate      true = OTP_HMAC_KEY đặt riêng; false = suy ra từ CRYPTO_MASTER_KEY
     * @param identityKeySeparate true = IDENTITY_HMAC_KEY đặt riêng; false = suy ra từ CRYPTO_MASTER_KEY
     */
    public record Crypto(String dataEncryption, boolean masterKeyConfigured, String passwordHashing,
                         String otpHashing, boolean otpKeySeparate, boolean identityKeySeparate) {
    }

    /** provider: local | s3. bucket chỉ có khi s3; không trả endpoint (chứa account id của R2). */
    public record Storage(String provider, String bucket, boolean encryptedAtRest) {
    }

    /** Khai báo qua biến môi trường APP_BACKUP_*; app không tự sao lưu, chỉ hiện cho Admin biết. */
    public record Backup(String provider, Integer retentionDays, String note) {
    }
}
