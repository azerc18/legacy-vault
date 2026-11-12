package com.ltld.app.legacyvault.dto.executordto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Từ chối lời mời làm Executor; lý do không bắt buộc, Owner xem trên trang Người thi hành. */
@Getter
@Setter
@NoArgsConstructor
public class DeclineExecutorInvitationRequest {
    @Size(max = 500)
    private String reason;
}
