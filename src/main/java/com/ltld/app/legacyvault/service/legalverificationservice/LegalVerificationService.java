package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LegalVerificationService {

    // UC12: danh sách hồ sơ đang chờ duyệt (loại trừ xung đột lợi ích)
    List<VerificationRequestResponseDto> getPendingRequests(UUID verifierId);

    // UC12: xem chi tiết hồ sơ (404 nếu xung đột lợi ích)
    VerificationRequestResponseDto getById(UUID requestId, UUID verifierId);

    // UC13: xác thực tài liệu (mock, 404 nếu xung đột lợi ích)
    DocumentVerificationResponseDto verifyDocument(UUID requestId, UUID verifierId);

    // UC12: phê duyệt hồ sơ (chưa mở khóa Vault)
    VerificationRequestResponseDto approve(UUID requestId, UUID verifierId);

    // UC12: từ chối hồ sơ
    VerificationRequestResponseDto reject(UUID requestId, UUID verifierId, RejectVerificationRequestDto dto);

    // UC14: gửi OTP ký xác nhận mở khóa Vault qua email của verifier
    void sendSignOtp(UUID requestId, UUID verifierId);

    // UC14: ký xác nhận, mở khóa Vault
    VerificationRequestResponseDto sign(UUID requestId, UUID verifierId, SignVerificationRequestDto dto);

    // UC15: xem lịch sử các hồ sơ đã xử lý (lọc + phân trang trong DB)
    List<VerificationRequestResponseDto> getHistory(
            UUID verifierId, LegalVerificationStatus status, LocalDate from, LocalDate to,
            int page, int size);

    // D1: tải/xem giấy chứng tử (đã giải mã)
    VerificationDocumentDto getDocument(UUID requestId, UUID verifierId);
}