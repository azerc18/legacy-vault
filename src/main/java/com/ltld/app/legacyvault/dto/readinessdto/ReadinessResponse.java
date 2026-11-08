package com.ltld.app.legacyvault.dto.readinessdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessResponse {
    private int score;
    private int maxScore;
    private String grade;
    private int completedItems;
    private int totalItems;
    private List<ReadinessItemResponse> items;
    // PR 2: số tài sản đã hết hạn xác thực (cần xác thực lại) và số tài sản sắp hết hạn trong 14 ngày
    private int assetsNeedReverification;
    private int assetsExpiringSoon;
}

