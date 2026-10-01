package com.ltld.app.legacyvault.service.impl;

import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.LegalVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LegalVerificationServiceImpl implements LegalVerificationService {

    private final LegalVerificationRequestRepository requestRepository;
    private final DigitalSignatureRepository signatureRepository;
    private final VerificationRequestVaultRepository requestVaultRepository;

    @Override
    public List<VerificationRequestResponseDto> getPendingRequests() {
        return requestRepository.findByStatus(VerificationStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Override
    public VerificationRequestResponseDto getById(UUID requestId) {
        return toDto(findRequestOrThrow(requestId));
    }

    @Override
    @Transactional
    public VerificationRequestResponseDto approveAndSign(UUID requestId, UUID verifierId, SignVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);

        // Chữ ký mock: chỉ cần đủ 6 số là hợp lệ (đã validate ở DTO),
        // signatureHash sinh giả lập, không phải chữ ký số thật
        DigitalSignature signature = DigitalSignature.builder()
                .request(request)
                .verifier(User.builder().id(verifierId).build())
                .signatureMethod(dto.getMethod())
                .signatureHash(UUID.randomUUID().toString())
                .build();
        signatureRepository.save(signature);

        LocalDateTime now = LocalDateTime.now();

        request.setStatus(VerificationStatus.APPROVED);
        request.setDecidedAt(now);
        request.setVerifier(User.builder().id(verifierId).build());

        // Mở khóa các Vault thuộc hồ sơ này (FR-14)
        requestVaultRepository.findByRequestId(requestId).forEach(rv -> {
            Vault vault = rv.getVault();
            vault.setStatus(VaultStatus.UNLOCKED);
            vault.setUnlockedAt(now);
        });

        return toDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    public VerificationRequestResponseDto reject(UUID requestId, UUID verifierId, RejectVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);

        request.setStatus(VerificationStatus.REJECTED);
        request.setRejectionReason(dto.getReason());
        request.setDecidedAt(LocalDateTime.now());
        request.setVerifier(User.builder().id(verifierId).build());

        return toDto(requestRepository.save(request));
    }

    private LegalVerificationRequest findRequestOrThrow(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + requestId));
    }

    private void assertPending(LegalVerificationRequest request) {
        if (request.getStatus() != VerificationStatus.PENDING) {
            throw new RequestAlreadyReviewedException("Hồ sơ này đã được xử lý trước đó");
        }
    }

    private VerificationRequestResponseDto toDto(LegalVerificationRequest r) {
        return VerificationRequestResponseDto.builder()
                .id(r.getId())
                .ownerName(r.getOwner().getFullName())
                .executorName(r.getExecutor().getFullName())
                .status(r.getStatus())
                .deathCertificateFileUrlEncrypted(r.getDeathCertificateFileUrlEncrypted())
                .rejectionReason(r.getRejectionReason())
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}