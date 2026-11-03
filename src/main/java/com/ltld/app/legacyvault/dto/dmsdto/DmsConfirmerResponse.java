package com.ltld.app.legacyvault.dto.dmsdto;

import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsConfirmerResponse {
    private UUID id;
    private String name;
    private String email;
    private String relationship;
    private DmsConfirmerStatus status;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;
}

