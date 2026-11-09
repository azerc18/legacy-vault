package com.ltld.app.legacyvault.dto.paymentdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotBlank(message = "Plan code is required")
    private String planCode;

    // VNPAY hoặc PAYPAL; để trống là VNPAY (client cũ không gửi trường này)
    private String provider;
}

