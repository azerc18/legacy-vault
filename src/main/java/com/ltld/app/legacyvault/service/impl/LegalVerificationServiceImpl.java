package com.ltld.app.legacyvault.service.impl;

import com.ltld.app.legacyvault.dto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.RequestNotApprovedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.LegalVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

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
        return requestRepository.findByStatus(LegalVerificationStatus.PENDING)
                .stream().map(this::toDto).toList();
    }

    @Override
    public VerificationRequestResponseDto getById(UUID requestId) {
        return toDto(findRequestOrThrow(requestId));
    }

    // UC12: chỉ đổi trạng thái, chưa ký và chưa mở khóa Vault
    @Override
    @Transactional
    public VerificationRequestResponseDto approve(UUID requestId, UUID verifierId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);

        request.setStatus(LegalVerificationStatus.APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setVerifier(User.builder().id(verifierId).build());

        return toDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    public VerificationRequestResponseDto reject(UUID requestId, UUID verifierId, RejectVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);

        request.setStatus(LegalVerificationStatus.REJECTED);
        request.setRejectionReason(dto.getReason());
        request.setDecidedAt(LocalDateTime.now());
        request.setVerifier(User.builder().id(verifierId).build());

        return toDto(requestRepository.save(request));
    }

    // UC13: gọi mock service kiểm tra tài liệu của hồ sơ
    @Override
    public DocumentVerificationResponseDto verifyDocument(UUID requestId) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        boolean valid = mockVerifyDocument(request.getDeathCertificateFileUrlEncrypted());

        return DocumentVerificationResponseDto.builder()
                .requestId(requestId)
                .valid(valid)
                .message(valid
                        ? "Tài liệu hợp lệ"
                        : "Tài liệu không hợp lệ (chỉ chấp nhận .pdf, .jpg, .png)")
                .build();
    }

    // UC14: hồ sơ phải APPROVED và chưa ký mới được ký
    @Override
    @Transactional
    public VerificationRequestResponseDto sign(UUID requestId, UUID verifierId, SignVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);

        if (request.getStatus() != LegalVerificationStatus.APPROVED) {
            throw new RequestNotApprovedException("Hồ sơ phải được phê duyệt trước khi ký");
        }
        if (signatureRepository.existsByRequestId(requestId)) {
            throw new RequestAlreadyReviewedException("Hồ sơ này đã được ký trước đó");
        }

        // Chữ ký giả lập: băm SHA-256 của (hồ sơ, người ký, phương thức, giấy chứng tử)
        DigitalSignature signature = DigitalSignature.builder()
                .request(request)
                .verifier(User.builder().id(verifierId).build())
                .signatureMethod(dto.getMethod())
                .signatureHash(SignatureHasher.hash(
                        requestId, verifierId, dto.getMethod(), request.getDeathCertificateFileUrlEncrypted()))
                .build();
        signatureRepository.save(signature);

        // Mở khóa các Vault thuộc hồ sơ này
        LocalDateTime now = LocalDateTime.now();
        requestVaultRepository.findByRequestId(requestId).forEach(rv -> {
            Vault vault = rv.getVault();
            vault.setStatus(VaultStatus.UNLOCKED);
            vault.setUnlockedAt(now);
        });

        return toDto(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationRequestResponseDto> getHistory(
            UUID verifierId, LegalVerificationStatus status, LocalDate from, LocalDate to) {

        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        // "to" tính trọn ngày, nên lấy đầu ngày kế tiếp làm mốc
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();

        return requestRepository.findByVerifierIdOrderByDecidedAtDesc(verifierId).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> fromTime == null
                        || (r.getDecidedAt() != null && !r.getDecidedAt().isBefore(fromTime)))
                .filter(r -> toTime == null
                        || (r.getDecidedAt() != null && r.getDecidedAt().isBefore(toTime)))
                .map(this::toDto)
                .toList();
    }

    // Mock: hợp lệ nếu đường dẫn kết thúc bằng .pdf, .jpg hoặc .png
    private boolean mockVerifyDocument(String documentRef) {
        if (documentRef == null) {
            return false;
        }
        String ref = documentRef.toLowerCase();
        return ref.endsWith(".pdf") || ref.endsWith(".jpg") || ref.endsWith(".png");
    }

    private LegalVerificationRequest findRequestOrThrow(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new VerificationRequestNotFoundException("Không tìm thấy hồ sơ: " + requestId));
    }

    private void assertPending(LegalVerificationRequest request) {
        if (request.getStatus() != LegalVerificationStatus.PENDING) {
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