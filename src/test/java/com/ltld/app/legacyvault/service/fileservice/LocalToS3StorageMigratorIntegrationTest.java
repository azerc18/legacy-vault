package com.ltld.app.legacyvault.service.fileservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H1: chuyển file cũ trên đĩa lên bucket và đổi đường dẫn trong DB. Bảng chỉ có đúng cột đường dẫn mà migrator đọc,
 * nên không cần dựng cả Spring context.
 */
@Testcontainers(disabledWithoutDocker = true)
class LocalToS3StorageMigratorIntegrationTest {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Container
    static GenericContainer<?> minio = S3TestContainers.minio();

    @TempDir
    Path uploadDir;

    JdbcTemplate jdbc;
    S3FileStorageService storage;
    LocalToS3StorageMigrator migrator;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        for (String[] tc : LocalToS3StorageMigrator.PATH_COLUMNS) {
            jdbc.execute("DROP TABLE IF EXISTS " + tc[0]);
            jdbc.execute("CREATE TABLE " + tc[0] + " (id INT AUTO_INCREMENT PRIMARY KEY, " + tc[1] + " VARCHAR(500))");
        }
        storage = S3TestContainers.storage(minio, "migration-test");
        storage.ensureBucket();
        migrator = new LocalToS3StorageMigrator(jdbc, storage, uploadDir.toString(), false);
    }

    private Path localFile(String name, byte[] content) throws Exception {
        return Files.write(uploadDir.resolve(name), content);
    }

    private String valueOf(String table, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table, String.class);
    }

    @Test
    @DisplayName("H1: file cũ được đưa lên bucket, DB đổi sang key; giá trị đã là key, null hoặc thiếu file thì giữ nguyên")
    void migrate_movesLegacyFilesAndRewritesPaths() throws Exception {
        Path avatar = localFile("avatar-1.png", new byte[]{1, 2, 3});
        Path will = localFile("will-1.pdf", new byte[]{9, 9});
        localFile("moved-1.pdf", new byte[]{5});
        jdbc.update("INSERT INTO users (avatar_path) VALUES (?)", avatar.toString());
        jdbc.update("INSERT INTO legal_documents (file_url_encrypted) VALUES (?)", will.toString());
        // Thư mục cũ đã đổi chỗ: tìm theo tên file trong upload-dir hiện tại
        jdbc.update("INSERT INTO legal_verification_requests (death_certificate_file_url_encrypted) VALUES (?)",
                "/old/server/uploads/moved-1.pdf");
        jdbc.update("INSERT INTO digital_assets (attachment_url) VALUES (?)", uploadDir.resolve("gone.pdf").toString());
        jdbc.update("INSERT INTO verification_exchange_messages (attachment_path) VALUES (?)", "already-a-key.pdf");
        jdbc.update("INSERT INTO verification_exchange_messages (attachment_path) VALUES (NULL)");

        int migrated = migrator.migrate();

        assertThat(migrated).isEqualTo(3);
        assertThat(valueOf("users", "avatar_path")).isEqualTo("avatar-1.png");
        assertThat(valueOf("legal_documents", "file_url_encrypted")).isEqualTo("will-1.pdf");
        assertThat(valueOf("legal_verification_requests", "death_certificate_file_url_encrypted")).isEqualTo("moved-1.pdf");
        assertThat(storage.readFile("avatar-1.png")).containsExactly(1, 2, 3);
        assertThat(storage.readFile("will-1.pdf")).containsExactly(9, 9);
        assertThat(storage.readFile("moved-1.pdf")).containsExactly(5);
        // Thiếu file: giữ nguyên để không mất dấu; key có sẵn và NULL không bị đụng tới
        assertThat(valueOf("digital_assets", "attachment_url")).isEqualTo(uploadDir.resolve("gone.pdf").toString());
        assertThat(jdbc.queryForList("SELECT attachment_path FROM verification_exchange_messages ORDER BY id", String.class))
                .containsExactly("already-a-key.pdf", null);
        // File gốc trên đĩa được giữ làm bản sao lưu
        assertThat(avatar).exists();
    }

    @Test
    @DisplayName("H1: chạy lại lần hai không chuyển thêm gì")
    void migrate_isIdempotent() throws Exception {
        Path avatar = localFile("avatar-2.png", new byte[]{4});
        jdbc.update("INSERT INTO users (avatar_path) VALUES (?)", avatar.toString());

        assertThat(migrator.migrate()).isEqualTo(1);
        assertThat(migrator.migrate()).isZero();
        assertThat(valueOf("users", "avatar_path")).isEqualTo("avatar-2.png");
    }
}
