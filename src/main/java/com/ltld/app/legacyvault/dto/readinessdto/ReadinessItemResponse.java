package com.ltld.app.legacyvault.dto.readinessdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessItemResponse {
    private String key;
    private String titleKey;
    private String title;
    private boolean done;
    private int points;
    private String fixUrl;
}

