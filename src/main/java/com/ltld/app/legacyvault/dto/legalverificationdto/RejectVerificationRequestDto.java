package com.ltld.app.legacyvault.dto.legalverificationdto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ltld.app.legacyvault.enums.RejectionReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectVerificationRequestDto {

    @NotNull(message = "Vui lòng chọn lý do từ chối")
    private RejectionReason reason;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;

    // Chọn "Lý do khác" thì bắt buộc nhập ghi chú
    @JsonIgnore
    @AssertTrue(message = "Vui lòng nhập ghi chú khi chọn lý do khác")
    public boolean isNoteProvidedWhenOther() {
        return reason != RejectionReason.OTHER || (note != null && !note.isBlank());
    }
}
