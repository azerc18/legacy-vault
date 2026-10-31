package com.ltld.app.legacyvault.service.fileservice;

import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * H1: lưu file lên object storage tương thích S3 (MinIO khi phát triển, Cloudflare R2 / AWS S3 khi demo).
 * Bật bằng {@code app.storage.provider=s3}; mặc định vẫn là {@link FileStorageServiceImpl} (đĩa local).
 *
 * <p>Không mã hóa ở đây: mọi nơi gọi đã mã hóa AES-GCM trước {@code storeFile} và giải mã sau {@code readFile}
 * (tài liệu pháp lý, giấy chứng tử, tệp tin nhắn), nên object trên bucket luôn là bản mã. Mã hóa thêm ở đây sẽ
 * thành mã hóa hai lần.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    // Chỉ giữ đuôi file ngắn, chữ và số: key trên bucket không được chứa '/' hay '\'
    private static final Pattern SAFE_EXTENSION = Pattern.compile("\\.[A-Za-z0-9]{1,10}");
    private static final String UNAVAILABLE = "File storage is unavailable. Please try again later.";

    private final S3Client s3;
    private final String bucket;

    @Autowired
    public S3FileStorageService(@Value("${app.storage.s3.endpoint:}") String endpoint,
                                @Value("${app.storage.s3.region:us-east-1}") String region,
                                @Value("${app.storage.s3.bucket}") String bucket,
                                @Value("${app.storage.s3.access-key}") String accessKey,
                                @Value("${app.storage.s3.secret-key}") String secretKey,
                                @Value("${app.storage.s3.path-style:true}") boolean pathStyle) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyle)
                // MinIO / R2 chưa hỗ trợ đủ checksum mặc định mới của SDK: chỉ tính khi API bắt buộc
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3 = builder.build();
        this.bucket = bucket;
    }

    // Cho unit test truyền S3Client giả
    S3FileStorageService(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Tạo bucket nếu chưa có (MinIO local). Không kết nối được thì chỉ log, các request sau sẽ nhận 503. */
    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(r -> r.bucket(bucket));
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (SdkException e) {
            log.error("H1: cannot reach bucket {} at startup: {}", bucket, e.getMessage());
        }
    }

    // Khóa chỉ có quyền trên object (hay gặp với R2) không tạo được bucket: chỉ log, không làm app dừng khởi động
    private void createBucket() {
        try {
            s3.createBucket(r -> r.bucket(bucket));
            log.info("H1: created bucket {}", bucket);
        } catch (SdkException e) {
            log.error("H1: bucket {} does not exist and cannot be created (create it in the provider console): {}",
                    bucket, e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        s3.close();
    }

    @Override
    public String storeFile(MultipartFile originalFile, byte[] encryptedContent) {
        String key = UUID.randomUUID() + extensionOf(originalFile.getOriginalFilename());
        put(key, encryptedContent);
        return key;
    }

    @Override
    public byte[] readFile(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path is empty.");
        }
        try {
            return s3.getObjectAsBytes(r -> r.bucket(bucket).key(keyOf(filePath))).asByteArray();
        } catch (NoSuchKeyException e) {
            // Cùng hợp đồng với bản local: nơi gọi bắt IOException để trả 404 / 410
            throw new FileNotFoundException("Stored file not found.");
        } catch (SdkException e) {
            throw new StorageUnavailableException(UNAVAILABLE, e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            s3.deleteObject(r -> r.bucket(bucket).key(keyOf(filePath)));
        } catch (SdkException e) {
            // Giống bản local: gọi sau commit/rollback nên chỉ log, để lại object mồ côi (vô hại)
            log.warn("H1: could not delete object {}: {}", keyOf(filePath), e.getMessage());
        }
    }

    /** Ghi một object theo key cho sẵn; dùng chung cho storeFile và việc chuyển file cũ lên bucket. */
    void put(String key, byte[] content) {
        try {
            s3.putObject(r -> r.bucket(bucket).key(key), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageUnavailableException(UNAVAILABLE, e);
        }
    }

    boolean exists(String key) {
        try {
            s3.headObject(r -> r.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            throw new StorageUnavailableException(UNAVAILABLE, e);
        }
    }

    /**
     * Key trên bucket là tên file (UUID + đuôi). Đường dẫn cũ trong DB (trước H1) là đường dẫn tuyệt đối trên đĩa;
     * lấy phần tên file để đọc được cả khi việc chuyển DB chưa chạy xong.
     */
    static String keyOf(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(slash + 1);
    }

    static boolean isLegacyPath(String path) {
        return path.indexOf('/') >= 0 || path.indexOf('\\') >= 0;
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        String ext = dot < 0 ? "" : originalFilename.substring(dot);
        return SAFE_EXTENSION.matcher(ext).matches() ? ext.toLowerCase() : "";
    }
}
