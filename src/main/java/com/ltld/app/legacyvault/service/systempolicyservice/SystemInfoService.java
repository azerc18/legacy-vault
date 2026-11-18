package com.ltld.app.legacyvault.service.systempolicyservice;

import com.ltld.app.legacyvault.dto.systempolicydto.SystemInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Thông tin CHỈ ĐỌC cho trang Chính sách hệ thống: cách dữ liệu được mã hoá, nơi lưu tệp, sao lưu CSDL,
 * phiên bản migration. Không trả khoá, mật khẩu hay endpoint: chỉ cho biết đã đặt hay chưa.
 *
 * <p>Sao lưu do hạ tầng làm (vd. Azure Database for MySQL tự sao lưu), app không tự sao lưu; người deploy khai báo
 * qua APP_BACKUP_PROVIDER / APP_BACKUP_RETENTION_DAYS / APP_BACKUP_NOTE để Admin biết. Để trống thì trang hiện
 * "Chưa khai báo".
 */
@Slf4j
@Service
public class SystemInfoService {

    // Khớp CryptoServiceImpl (AES/GCM/NoPadding, khoá 32 byte), BCryptPasswordEncoder (SecurityConfig), OtpHasher
    static final String DATA_ENCRYPTION = "AES-256-GCM";
    static final String PASSWORD_HASHING = "BCrypt";
    static final String OTP_HASHING = "HMAC-SHA256";

    private final ObjectProvider<Flyway> flyway;
    private final boolean masterKeyConfigured;
    private final boolean otpKeySeparate;
    private final boolean identityKeySeparate;
    private final String storageProvider;
    private final String bucket;
    private final String backupProvider;
    private final Integer backupRetentionDays;
    private final String backupNote;

    public SystemInfoService(ObjectProvider<Flyway> flyway,
                             @Value("${app.security.crypto.master-key:}") String masterKey,
                             @Value("${app.security.otp-hmac-key:}") String otpKey,
                             @Value("${app.security.identity-hmac-key:}") String identityKey,
                             @Value("${app.storage.provider:local}") String storageProvider,
                             @Value("${app.storage.s3.bucket:}") String bucket,
                             @Value("${app.backup.provider:}") String backupProvider,
                             @Value("${app.backup.retention-days:}") String backupRetentionDays,
                             @Value("${app.backup.note:}") String backupNote) {
        this.flyway = flyway;
        this.masterKeyConfigured = notBlank(masterKey);
        this.otpKeySeparate = notBlank(otpKey);
        this.identityKeySeparate = notBlank(identityKey);
        this.storageProvider = storageProvider == null ? "local" : storageProvider.trim().toLowerCase(Locale.ROOT);
        this.bucket = "s3".equals(this.storageProvider) && notBlank(bucket) ? bucket.trim() : null;
        this.backupProvider = notBlank(backupProvider) ? backupProvider.trim() : null;
        this.backupRetentionDays = parsePositive(backupRetentionDays);
        this.backupNote = notBlank(backupNote) ? backupNote.trim() : null;
    }

    public SystemInfoResponse get() {
        return new SystemInfoResponse(
                new SystemInfoResponse.Crypto(DATA_ENCRYPTION, masterKeyConfigured, PASSWORD_HASHING, OTP_HASHING,
                        otpKeySeparate, identityKeySeparate),
                // Mọi nơi gọi storeFile đều mã hoá AES-GCM trước (xem ghi chú ở S3FileStorageService)
                new SystemInfoResponse.Storage(storageProvider, bucket, true),
                new SystemInfoResponse.Backup(backupProvider, backupRetentionDays, backupNote),
                schemaVersion());
    }

    private String schemaVersion() {
        try {
            Flyway f = flyway.getIfAvailable();
            if (f == null) return null;
            MigrationInfo current = f.info().current();
            return current == null || current.getVersion() == null ? null : current.getVersion().getVersion();
        } catch (RuntimeException e) {
            log.warn("Could not read Flyway schema version: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static Integer parsePositive(String s) {
        if (!notBlank(s)) return null;
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
