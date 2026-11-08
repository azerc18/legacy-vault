package com.ltld.app.legacyvault.service.ocrservice;

import com.ltld.app.legacyvault.dto.ocrdto.DeathCertificateOcrResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DeathCertificateOcrService {
    DeathCertificateOcrResponseDto analyzeForExecutor(UUID assignmentId, String executorLogin, MultipartFile file);
    DeathCertificateOcrResponseDto analyzeForVerifier(UUID requestId, UUID verifierId);
}
