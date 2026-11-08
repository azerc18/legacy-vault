package com.ltld.app.legacyvault.dto.adminoverviewdto;

import com.ltld.app.legacyvault.enums.PaymentStatus;

import java.time.LocalDate;

/** Bộ lọc giao dịch dùng chung cho danh sách, tổng hợp và xuất CSV. from/to tính theo ngày tạo, to là ngày cuối (bao gồm). */
public record AdminPaymentFilter(PaymentStatus status, String email, LocalDate from, LocalDate to) {
}
