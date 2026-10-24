package com.ltld.app.legacyvault.dto.legalverificationdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Số lượng hồ sơ theo trạng thái của verifier (đếm trong DB, không bị giới hạn phân trang). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifierSummaryResponseDto {
    private long pending;            // chờ duyệt (đã loại hồ sơ xung đột lợi ích)
    private long awaitingSignature;  // mình đã duyệt, chờ ký
    private long signed;
    private long rejected;
}
