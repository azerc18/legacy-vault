package com.ltld.app.legacyvault.service.fileservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// H1: bản mặc định (đĩa local); app.storage.provider=s3 thì dùng S3FileStorageService
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class FileStorageServiceImpl implements FileStorageService {

    // Thư mục lưu file trên ổ cứng. Nếu file cấu hình không có, mặc định lưu vào folder 'uploads/'
    @Value("${app.storage.upload-dir:uploads/}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile originalFile, byte[] encryptedContent) throws Exception {
        // 1. Tạo thư mục uploads/ nếu nó chưa tồn tại trên máy chủ
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 2. Bảo mật: KHÔNG dùng getOriginalFilename() trực tiếp để tránh Path Traversal.
        String originalFilename = originalFile.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String safeFileName = UUID.randomUUID().toString() + extension;
        Path targetLocation = uploadPath.resolve(safeFileName).normalize();

        if (!targetLocation.startsWith(uploadPath)) {
            throw new RuntimeException("Security Error: Cannot store file outside current directory.");
        }

        // 3. Ghi mảng byte (đã được mã hóa) xuống ổ cứng
        Files.write(targetLocation, encryptedContent);

        // 4. Trả về đường dẫn của file để lát nữa chúng ta lưu vào Database
        return targetLocation.toString();
    }

    @Override
    public byte[] readFile(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path is empty.");
        }
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = Paths.get(filePath).toAbsolutePath().normalize();

        // Chỉ cho đọc file nằm trong thư mục upload, tránh đọc file hệ thống qua đường dẫn bị chỉnh sửa
        if (!target.startsWith(uploadPath)) {
            throw new SecurityException("Security Error: Cannot read file outside the upload directory.");
        }
        if (!Files.isRegularFile(target)) {
            throw new java.io.FileNotFoundException("Stored file not found.");
        }
        // Chặn symlink nằm trong thư mục upload nhưng trỏ ra ngoài
        if (!target.toRealPath().startsWith(uploadPath.toRealPath())) {
            throw new SecurityException("Security Error: Cannot read file outside the upload directory.");
        }
        return Files.readAllBytes(target);
    }

    @Override
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return;
        try {
            Path fileToDelete = Paths.get(filePath).toAbsolutePath().normalize();
            Files.deleteIfExists(fileToDelete);
        } catch (Exception e) {
            System.err.println("Failed to delete file: " + filePath);
        }
    }
}
