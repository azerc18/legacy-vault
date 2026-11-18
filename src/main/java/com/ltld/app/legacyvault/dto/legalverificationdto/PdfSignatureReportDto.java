package com.ltld.app.legacyvault.dto.legalverificationdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * V2: kết quả đọc chữ ký số (PAdES / PKCS#7) nhúng trong giấy chứng tử dạng PDF.
 * Chỉ để Verifier tham khảo: KHÔNG tự động duyệt hay từ chối hồ sơ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfSignatureReportDto {

    public enum DocumentKind { PDF, IMAGE, OTHER }

    /** Tổng kết cho FE chọn màu: không có chữ ký / tất cả hợp lệ / có chữ ký lỗi / có chữ ký chưa kiểm được. */
    public enum Overall { NO_SIGNATURE, ALL_VALID, HAS_INVALID, HAS_UNVERIFIED, NOT_PDF, UNREADABLE }

    private UUID requestId;
    private DocumentKind documentKind;
    private Overall overall;
    private int signatureCount;
    private List<SignatureInfo> signatures;
    // Lời nhắc chung (ví dụ: chưa cấu hình danh sách CA tin cậy)
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignatureInfo {
        private String fieldName;
        private String subFilter;            // adbe.pkcs7.detached, ETSI.CAdES.detached, ...
        private String signerName;           // CN của chứng thư, hoặc /Name trong chữ ký
        private String signerOrganization;   // O của chứng thư
        private String issuer;               // CN (hoặc DN) của nơi cấp chứng thư
        private String serialNumber;
        private LocalDateTime signedAt;      // signingTime trong chữ ký, hoặc /M
        private LocalDateTime certificateNotBefore;
        private LocalDateTime certificateNotAfter;
        private String digestAlgorithm;
        private String reason;
        private String location;

        // Nội dung trong vùng đã ký còn nguyên (mã băm khớp messageDigest và chữ ký khớp khóa công khai)
        private Boolean integrityValid;
        // Chữ ký phủ toàn bộ file (không có phần nào thêm vào sau khi ký)
        private Boolean coversWholeDocument;
        // Chứng thư còn hiệu lực tại thời điểm ký
        private Boolean certificateValidAtSigning;
        // Chứng thư tự ký (không do CA nào cấp) -> độ tin cậy thấp
        private Boolean selfSigned;
        // Lý do khi không kiểm được (thuật toán chưa hỗ trợ, dữ liệu hỏng...)
        private String problem;
    }
}
