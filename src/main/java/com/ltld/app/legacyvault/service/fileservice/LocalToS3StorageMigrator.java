package com.ltld.app.legacyvault.service.fileservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * H1: chuyển một lần các file đang nằm trên đĩa (trước H1) lên bucket, rồi đổi giá trị trong DB từ đường dẫn tuyệt đối
 * sang key. Chạy khi khởi động nếu {@code app.storage.migrate-local=true}; chạy lại không làm gì thêm (chỉ xử lý giá trị
 * còn là đường dẫn). File gốc trên đĩa được giữ lại làm bản sao lưu.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class LocalToS3StorageMigrator implements ApplicationRunner {

    /** Mọi cột đang lưu đường dẫn file do FileStorageService trả về: { bảng, cột }. */
    static final List<String[]> PATH_COLUMNS = List.of(
            new String[]{"legal_documents", "file_url_encrypted"},
            new String[]{"legal_verification_requests", "death_certificate_file_url_encrypted"},
            new String[]{"verification_exchange_messages", "attachment_path"},
            new String[]{"digital_assets", "attachment_url"},
            new String[]{"users", "avatar_path"});

    private final JdbcTemplate jdbc;
    private final S3FileStorageService storage;
    private final String uploadDir;
    private final boolean enabled;

    public LocalToS3StorageMigrator(JdbcTemplate jdbc, S3FileStorageService storage,
                                    @Value("${app.storage.upload-dir:uploads/}") String uploadDir,
                                    @Value("${app.storage.migrate-local:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.uploadDir = uploadDir;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            migrate();
        }
    }

    /** @return số giá trị đã chuyển sang key */
    public int migrate() {
        int migrated = 0;
        int missing = 0;
        for (String[] tc : PATH_COLUMNS) {
            String table = tc[0];
            String column = tc[1];
            List<String> values = jdbc.queryForList(
                    "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + " IS NOT NULL", String.class);
            for (String path : values) {
                if (!S3FileStorageService.isLegacyPath(path)) {
                    continue;
                }
                Path file = locate(path);
                if (file == null) {
                    missing++;
                    log.warn("H1: {}.{} points to a missing file, left unchanged: {}", table, column, path);
                    continue;
                }
                String key = S3FileStorageService.keyOf(path);
                try {
                    if (!storage.exists(key)) {
                        storage.put(key, Files.readAllBytes(file));
                    }
                } catch (IOException e) {
                    missing++;
                    log.warn("H1: cannot read {}, left unchanged: {}", file, e.getMessage());
                    continue;
                }
                migrated += jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?", key, path);
            }
        }
        log.info("H1: moved {} file reference(s) to the bucket, {} skipped (file not found)", migrated, missing);
        return migrated;
    }

    // Đường dẫn tuyệt đối như đã lưu; nếu thư mục đã đổi chỗ thì thử tìm theo tên file trong upload-dir hiện tại
    private Path locate(String path) {
        Path stored = Paths.get(path);
        if (Files.isRegularFile(stored)) {
            return stored;
        }
        Path inUploadDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(S3FileStorageService.keyOf(path));
        return Files.isRegularFile(inUploadDir) ? inUploadDir : null;
    }
}
