package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ExecutorVerificationService {

    DeathVerificationResponse submit(UUID assignmentId, String executorLogin,
                                     MultipartFile file, CreateDeathVerificationRequest request);
}
