package com.ltld.app.legacyvault.service.fileservice;

import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** H1: S3FileStorageService chạy với MinIO thật (Testcontainers). */
@Testcontainers
class S3FileStorageServiceIntegrationTest {

    @Container
    static GenericContainer<?> minio = S3TestContainers.minio();

    static S3FileStorageService storage;

    @BeforeAll
    static void setUp() {
        storage = storageFor("legacyvault-test");
        storage.ensureBucket();
    }

    static S3FileStorageService storageFor(String bucket) {
        return S3TestContainers.storage(minio, bucket);
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/pdf", new byte[]{1});
    }

    @Test
    @DisplayName("H1: lưu rồi đọc lại đúng nội dung; key là tên file phẳng, giữ đuôi")
    void storeThenRead_returnsSameBytes() throws Exception {
        byte[] content = {10, 20, 30, 40};

        String key = storage.storeFile(file("Di chuc.PDF"), content);

        assertThat(key).endsWith(".pdf").doesNotContain("/", "\\");
        assertThat(storage.readFile(key)).isEqualTo(content);
    }

    @Test
    @DisplayName("H1: nơi gọi mã hóa trước khi lưu nên object trên bucket là bản mã, giải mã ra đúng bản gốc")
    void objectOnBucket_isCiphertext() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        CryptoServiceImpl crypto = new CryptoServiceImpl(Base64.getEncoder().encodeToString(key));
        byte[] plain = "%PDF-1.4 giay chung tu".getBytes(StandardCharsets.UTF_8);

        String stored = storage.storeFile(file("death-certificate.pdf"), crypto.encryptBytes(plain));
        byte[] onBucket = storage.readFile(stored);

        assertThat(onBucket).isNotEqualTo(plain);
        assertThat(new String(onBucket, StandardCharsets.ISO_8859_1)).doesNotContain("giay chung tu");
        assertThat(crypto.decryptBytes(onBucket)).isEqualTo(plain);
    }

    @Test
    @DisplayName("H1: đường dẫn kiểu cũ (tuyệt đối trên đĩa) vẫn đọc và xóa được theo tên file")
    void legacyAbsolutePath_readsByFileName() throws Exception {
        storage.put("legacy-file.pdf", new byte[]{7, 7});

        assertThat(storage.readFile("D:\\app\\uploads\\legacy-file.pdf")).containsExactly(7, 7);
        assertThat(storage.readFile("/data/legacyvault/uploads/legacy-file.pdf")).containsExactly(7, 7);

        storage.deleteFile("D:\\app\\uploads\\legacy-file.pdf");
        assertThatThrownBy(() -> storage.readFile("legacy-file.pdf")).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("H1: file không tồn tại -> FileNotFoundException (nơi gọi trả 404 / 410 như bản local)")
    void missingFile_throwsFileNotFound() {
        assertThatThrownBy(() -> storage.readFile("does-not-exist.pdf")).isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("H1: xóa xong không đọc được nữa; xóa đường dẫn rỗng hoặc null không lỗi")
    void delete_removesObject_andIgnoresBlank() throws Exception {
        String key = storage.storeFile(file("a.png"), new byte[]{1, 2});

        storage.deleteFile(key);

        assertThatThrownBy(() -> storage.readFile(key)).isInstanceOf(FileNotFoundException.class);
        assertThatCode(() -> storage.deleteFile(null)).doesNotThrowAnyException();
        assertThatCode(() -> storage.deleteFile(" ")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("H1: đuôi file lạ hoặc chứa ký tự đường dẫn bị bỏ, key không có '/' hay '\\'")
    void unsafeExtension_isDropped() throws Exception {
        String key = storage.storeFile(file("evil.pdf/../../x"), new byte[]{1});

        assertThat(key).doesNotContain("/", "\\", "..");
        assertThat(storage.readFile(key)).containsExactly(1);
    }

    @Test
    @DisplayName("H1: bucket chưa có thì tự tạo khi khởi động")
    void ensureBucket_createsMissingBucket() {
        S3FileStorageService other = storageFor("bucket-created-on-start");

        other.ensureBucket();

        assertThat(other.exists("nothing-here")).isFalse();
    }

    @Test
    @DisplayName("H1: không kết nối được storage -> StorageUnavailableException (503)")
    void unreachableStorage_throwsStorageUnavailable() {
        S3FileStorageService down = new S3FileStorageService("http://127.0.0.1:1", "us-east-1", "b",
                "user", "password", true);

        assertThatThrownBy(() -> down.storeFile(file("a.pdf"), new byte[]{1}))
                .isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> down.readFile("a.pdf")).isInstanceOf(StorageUnavailableException.class);
        assertThatCode(() -> down.deleteFile("a.pdf")).doesNotThrowAnyException();
    }
}
