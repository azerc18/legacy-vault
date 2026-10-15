package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SendVerificationMessageRequest {

    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String content;

    public SendVerificationMessageRequest(String content) {
        this.content = content;
    }
}
