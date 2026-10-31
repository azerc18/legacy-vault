package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.VerificationReviewProgress;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationReviewProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FR-13: ghi nhận kết quả Check đầu tiên trong transaction riêng. Hai request song song của cùng một verifier cùng tạo dòng
 * thì một bên vi phạm unique; transaction riêng giúp bên đó bắt lỗi mà không làm hỏng transaction kiểm tra tài liệu.
 */
@Component
@RequiredArgsConstructor
public class ReviewProgressRecorder {

    private final VerificationReviewProgressRepository progressRepository;
    private final LegalVerificationRequestRepository requestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChecked(UUID requestId, UUID verifierId, boolean valid) {
        progressRepository.saveAndFlush(VerificationReviewProgress.builder()
                .request(requestRepository.getReferenceById(requestId))
                .verifierId(verifierId)
                .checkedAt(LocalDateTime.now())
                .documentValid(valid)
                .build());
    }
}
