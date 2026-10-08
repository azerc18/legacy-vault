package com.ltld.app.legacyvault.service.fileservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    // Thư mục lưu file trên ổ cứng. Nếu file cấu hình không có, mặc định lưu vào folder 'uploads/'
    @Value("${app.storage.upload-dir:uploads/}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile originalFile, byte[] encryptedContent) throws Exception {
        // 1. Tạo thư mục uploads/ nếu nó chưa tồn tại trên máy chủ
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 2. Tạo tên file mới để tránh bị trùng tên (Ví dụ: abcd-1234_dichuc.pdf)
        String fileName = UUID.randomUUID().toString() + "_" + originalFile.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);

        // 3. Ghi mảng byte (đã được mã hóa) xuống ổ cứng
        Files.write(filePath, encryptedContent);

        // 4. Trả về đường dẫn của file để lát nữa chúng ta lưu vào Database
        return filePath.toString();
    }
}
