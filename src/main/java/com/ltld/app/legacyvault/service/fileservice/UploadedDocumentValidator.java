package com.ltld.app.legacyvault.service.fileservice;

import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
public class UploadedDocumentValidator {

    static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    public byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidVerificationDocumentException("Document file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidVerificationDocumentException("File exceeds the 10MB limit.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidVerificationDocumentException("Unable to read uploaded file.");
        }

        if (bytes.length > MAX_FILE_SIZE) {
            throw new InvalidVerificationDocumentException("File exceeds the 10MB limit.");
        }

        String detectedExtension = detectExtensionByMagicBytes(bytes);
        if (detectedExtension == null) {
            throw new InvalidVerificationDocumentException("Only pdf, png, jpg files are allowed.");
        }

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(detectedExtension) && !(detectedExtension.equals(".jpg") && fileName.endsWith(".jpeg"))) {
            throw new InvalidVerificationDocumentException("File extension does not match actual file content.");
        }

        return bytes;
    }

    private static String detectExtensionByMagicBytes(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return ".pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return ".png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        return null;
    }
}
