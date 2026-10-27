package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ltld.app.legacyvault.enums.RejectionReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectVerificationRequestDto {

    @NotNull(message = "Please select a rejection reason")
    private RejectionReason reason;

    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;

    // Chọn "Lý do khác" thì bắt buộc nhập ghi chú
    @JsonIgnore
    @AssertTrue(message = "Please enter a note when choosing another reason")
    public boolean isNoteProvidedWhenOther() {
        return reason != RejectionReason.OTHER || (note != null && !note.isBlank());
    }
}
