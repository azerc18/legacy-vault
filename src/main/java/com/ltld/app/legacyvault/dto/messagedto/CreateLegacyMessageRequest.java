package com.ltld.app.legacyvault.dto.messagedto;

import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLegacyMessageRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Recipient type is required")
    private LegacyMessageRecipientType recipientType;

    private UUID recipientBeneficiaryId;

    @NotNull(message = "Delivery trigger is required")
    private LegacyMessageDeliveryTrigger deliveryTrigger;

    private LocalDate deliverDate;
}

