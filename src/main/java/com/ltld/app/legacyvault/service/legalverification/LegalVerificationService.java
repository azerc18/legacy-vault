package com.ltld.app.legacyvault.service;

import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.VerificationRequestResponseDto;

import java.util.List;
import java.util.UUID;

public interface LegalVerificationService {

    List<VerificationRequestResponseDto> getPendingRequests();

    VerificationRequestResponseDto getById(UUID requestId);

    VerificationRequestResponseDto approveAndSign(UUID requestId, UUID verifierId, SignVerificationRequestDto dto);

    VerificationRequestResponseDto reject(UUID requestId, UUID verifierId, RejectVerificationRequestDto dto);
}