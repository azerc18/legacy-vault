package com.ltld.app.legacyvault.dto.paymentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private UUID transactionId;
    private String paymentUrl;
    private String planCode;
    private long amountVnd;
}
