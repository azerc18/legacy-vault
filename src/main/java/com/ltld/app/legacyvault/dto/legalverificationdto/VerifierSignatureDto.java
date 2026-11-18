package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.SignatureSealStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V3: thông tin chữ ký xác nhận + kết quả kiểm lại ngay lúc gọi API.
 * Trả đủ payload, chữ ký và khóa công khai để người ngoài tự kiểm bằng OpenSSL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifierSignatureDto {
    private UUID requestId;
    private UUID signatureId;
    private SignatureMethod method;
    private LocalDateTime signedAt;
    private String verifierName;
    private String verifierEmail;

    private SignatureSealStatus status;
    // null khi không kiểm được (chữ ký cũ, hoặc không đọc được file)
    private Boolean signatureValid;
    private Boolean payloadMatches;
    private Boolean documentMatches;
    // Ký tay: có ảnh chữ ký không, và ảnh đang lưu còn đúng mã băm lúc ký không (null khi không có ảnh)
    private boolean hasSignatureImage;
    private Boolean signatureImageMatches;
    private String signatureImageSha256;

    private String documentSha256;
    private String currentDocumentSha256;
    private String algorithm;
    private String keyRef;
    private String keyFingerprint;
    private String publicKeyPem;
    private String signedPayload;
    private String signatureValue;
    // Mã băm cũ (SHA-256 của payload với chữ ký mới; của các id với chữ ký cũ)
    private String signatureHash;
}
