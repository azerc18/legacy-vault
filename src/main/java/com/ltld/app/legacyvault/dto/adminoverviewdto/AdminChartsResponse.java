package com.ltld.app.legacyvault.dto.adminoverviewdto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** C2: số liệu cho biểu đồ — doanh thu theo ngày, người dùng mới theo tuần. */
@Getter
@Builder
public class AdminChartsResponse {
    private List<Point> revenueByDay;     // 30 ngày gần nhất, đủ cả ngày bằng 0
    private List<Point> newUsersByWeek;   // 12 tuần gần nhất, tuần bắt đầu thứ Hai

    @Getter
    @Builder
    public static class Point {
        private LocalDate date;
        private long value;
    }
}
