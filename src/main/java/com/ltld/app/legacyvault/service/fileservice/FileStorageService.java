package com.ltld.app.legacyvault.service.fileservice;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    // Nhận 1 file gốc (để lấy tên file) và mảng byte đã được mã hóa để lưu xuống ổ cứng
    String storeFile(MultipartFile originalFile, byte[] encryptedContent) throws Exception;
}
