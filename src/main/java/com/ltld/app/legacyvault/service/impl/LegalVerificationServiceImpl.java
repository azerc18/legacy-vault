package com.ltld.app.legacyvault.service.impl;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ConflictOfInterestException;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.RequestAlreadyReviewedException;
import com.ltld.app.legacyvault.exception.RequestNotApprovedException;
import com.ltld.app.legacyvault.exception.VaultStateConflictException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.DigitalSignatureRepository;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.LegalVerificationService;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
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

    private static final int CLAIM_TIMEOUT_DAYS = 60; // BR-04

    private final LegalVerificationRequestRepository requestRepository;
    private final DigitalSignatureRepository signatureRepository;
    private final VerificationRequestVaultRepository requestVaultRepository;
    private final AuditLogService auditLogService;

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
        assertNoConflictOfInterest(request, verifierId);

        request.setStatus(LegalVerificationStatus.APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setVerifier(User.builder().id(verifierId).build());

        return toDto(requestRepository.save(request));
    }

    // UC12: từ chối hồ sơ
    @Override
    @Transactional
    public VerificationRequestResponseDto reject(UUID requestId, UUID verifierId, RejectVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);
        assertPending(request);
        assertNoConflictOfInterest(request, verifierId);

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

    // UC14: hồ sơ phải APPROVED, do chính verifier đã duyệt ký, và chưa ký
    @Override
    @Transactional
    public VerificationRequestResponseDto sign(UUID requestId, UUID verifierId, SignVerificationRequestDto dto) {
        LegalVerificationRequest request = findRequestOrThrow(requestId);

        if (request.getStatus() != LegalVerificationStatus.APPROVED) {
            throw new RequestNotApprovedException("Hồ sơ phải được phê duyệt trước khi ký");
        }
        // Chỉ verifier đã phê duyệt mới được ký
        if (request.getVerifier() == null || !verifierId.equals(request.getVerifier().getId())) {
            throw new NotAssignedVerifierException("Chỉ Legal Verifier đã phê duyệt hồ sơ mới được ký.");
        }
        if (signatureRepository.existsByRequestId(requestId)) {
            throw new RequestAlreadyReviewedException("Hồ sơ này đã được ký trước đó");
        }

        // Mọi Vault của hồ sơ phải ở trạng thái ACTIVE và thuộc đúng chủ sở hữu; sai một cái là hủy cả
        List<VerificationRequestVault> links = requestVaultRepository.findByRequestIdFetchVault(requestId);
        if (links.isEmpty()) {
            throw new VaultStateConflictException("Hồ sơ không gắn với Vault nào.");
        }
        List<Vault> invalid = links.stream()
                .map(VerificationRequestVault::getVault)
                .filter(v -> v.getStatus() != VaultStatus.ACTIVE
                        || v.getOwner() == null
                        || !v.getOwner().getId().equals(request.getOwner().getId()))
                .toList();
        if (!invalid.isEmpty()) {
            throw new VaultStateConflictException(
                    "Có Vault không ở trạng thái cho phép mở khóa hoặc không thuộc chủ sở hữu của hồ sơ: "
                            + invalid.stream().map(v -> String.valueOf(v.getId())).toList());
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

        // Mở khóa Vault, đặt hạn nhận 60 ngày, mỗi Vault ghi một dòng audit
        LocalDateTime now = LocalDateTime.now();
        for (VerificationRequestVault link : links) {
            Vault vault = link.getVault();
            vault.setStatus(VaultStatus.UNLOCKED);
            vault.setUnlockedAt(now);
            vault.setClaimDeadlineAt(now.plusDays(CLAIM_TIMEOUT_DAYS));

            auditLogService.log(AuditAction.VAULT_UNLOCK, AuditResult.SUCCESS, verifierId, null,
                    "Vault",
                    vault.getId() == null ? null : vault.getId().toString(),
                    "unlocked by legal verifier, request " + requestId);
        }

        return toDto(request);
    }

    // UC15: lịch sử, lọc tùy chọn theo trạng thái và khoảng ngày
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

    // Verifier không được xử lý hồ sơ của chính mình (là chủ sở hữu hoặc executor)
    private void assertNoConflictOfInterest(LegalVerificationRequest request, UUID verifierId) {
        if (verifierId.equals(request.getOwner().getId())
                || verifierId.equals(request.getExecutor().getId())) {
            throw new ConflictOfInterestException("Bạn không được xử lý hồ sơ có liên quan đến chính mình.");
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