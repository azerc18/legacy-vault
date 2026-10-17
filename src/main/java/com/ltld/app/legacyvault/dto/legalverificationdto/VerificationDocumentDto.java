package com.ltld.app.legacyvault.dto.legalverificationdto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationDocumentDto {
    private final byte[] content;
    private final String contentType;
    private final String fileName;
}