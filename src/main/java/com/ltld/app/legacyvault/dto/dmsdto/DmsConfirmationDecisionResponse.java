package com.ltld.app.legacyvault.dto.dmsdto;

import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;
import com.ltld.app.legacyvault.enums.DmsStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsConfirmationDecisionResponse {
    private DmsStatus dmsStatus;
    private DmsConfirmerDecision decision;
    private String ownerName;
    private LocalDateTime postponedUntil;
    private LocalDateTime triggeredAt;
    private String message;
}

