package com.ltld.app.legacyvault.dto.beneficiarydto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

// X1: trạng thái đồng thuận K-of-N của một tài sản có điều kiện K-of-N
@Data
@Builder
public class ConsensusStatusResponse {
    private UUID assetId;
    private int threshold;      // K: số người cần đồng ý
    private int total;          // N: tổng số người nhận
    private int approved;       // số người đã đồng ý
    private boolean myApproved; // người đang xem đã đồng ý chưa
    private boolean reached;    // đã đủ K và ghép khóa hợp lệ
}
