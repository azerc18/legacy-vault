package com.ltld.app.legacyvault.dto.coupondto;

/** Việc 4: giá một gói sau khi áp mã. {@code finalVnd} là số tiền thật sẽ thanh toán. */
public record CouponQuote(String planCode, long originalVnd, long discountVnd, long finalVnd) {
}
