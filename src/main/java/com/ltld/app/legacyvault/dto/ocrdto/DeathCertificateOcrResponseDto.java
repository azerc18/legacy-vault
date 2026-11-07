package com.ltld.app.legacyvault.dto.ocrdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeathCertificateOcrResponseDto {
    private String documentCategory;
    private String documentType;
    /** Tiêu đề nguyên văn phát hiện bên trong tài liệu (VD: "TRÍCH LỤC KHAI TỬ (BẢN SAO)") */
    private String detectedHeaderTitle;
    /** Nhận xét về tiêu đề (VD: "Tiêu đề chuẩn mẫu hộ tịch TP/HT", "Tiêu đề y tế bổ trợ", ...) */
    private String titleAssessment;
    /** Điểm chất lượng & độ sắc nét hình ảnh/tài liệu (0 - 100%) */
    private int imageQualityScore;
    /** Đánh giá độ rõ nét (VD: "Rõ nét cao - Chữ và dấu hiển thị rõ ràng") */
    private String imageClarityLabel;
    /** Mức độ nội dung chứng minh được sự kiện qua đời của Chủ két (0 - 100%) */
    private int deathProofRelevancePercent;
    /** Diễn giải mức độ chứng minh sự qua đời */
    private String deathProofExplanation;
    /** Gợi ý tham khảo dành cho Executor (không bắt buộc) */
    private String executorSuggestion;
    /** Gợi ý tham khảo dành cho Legal Verifier (không quyết định thay Verifier) */
    private String verifierSuggestion;

    private String certificateNumber;
    private String bookNumber;
    private String deceasedFullName;
    private String dateOfBirth;
    private String gender;
    private String idNumber;
    private String dateOfDeath;
    private String placeOfDeath;
    private String causeOfDeath;
    private String issuingAuthority;
    private String issueDate;
    private String signerName;
    private boolean hasOfficialSeal;
    private boolean ownerNameMatched;
    private String expectedOwnerName;
    private int confidenceScore;
    private String suggestedRejectionCode;
    private String engineSource;
    private String summaryNote;
    private List<String> warnings;
}
