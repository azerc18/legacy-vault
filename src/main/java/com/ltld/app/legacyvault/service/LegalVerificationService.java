package com.ltld.app.legacyvault.service;

import com.ltld.app.legacyvault.dto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import java.time.LocalDate;

import java.util.List;
import java.util.UUID;

public interface LegalVerificationService {

    List<VerificationRequestResponseDto> getPendingRequests();

    VerificationRequestResponseDto getById(UUID requestId);

    // UC12: phê duyệt hồ sơ (chưa mở khóa Vault)
    VerificationRequestResponseDto approve(UUID requestId, UUID verifierId);

    // UC12: từ chối hồ sơ
    VerificationRequestResponseDto reject(UUID requestId, UUID verifierId, RejectVerificationRequestDto dto);

    // UC13: xác thực tài liệu (mock)
    DocumentVerificationResponseDto verifyDocument(UUID requestId);

    // UC14: ký xác nhận, mở khóa Vault
    VerificationRequestResponseDto sign(UUID requestId, UUID verifierId, SignVerificationRequestDto dto);

    // UC15
    List<VerificationRequestResponseDto> getHistory(
            UUID verifierId, LegalVerificationStatus status, LocalDate from, LocalDate to);
}