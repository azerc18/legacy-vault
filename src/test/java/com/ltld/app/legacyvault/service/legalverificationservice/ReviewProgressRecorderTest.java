package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.VerificationReviewProgress;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import com.ltld.app.legacyvault.repository.VerificationReviewProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewProgressRecorderTest {

    @Mock private VerificationReviewProgressRepository progressRepository;
    @Mock private LegalVerificationRequestRepository requestRepository;
    @InjectMocks private ReviewProgressRecorder recorder;

    @Test
    void recordChecked_createsRowForThisVerifierOnly() {
        UUID requestId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        LegalVerificationRequest ref = LegalVerificationRequest.builder().id(requestId).build();
        when(requestRepository.getReferenceById(requestId)).thenReturn(ref);

        recorder.recordChecked(requestId, verifierId, true);

        ArgumentCaptor<VerificationReviewProgress> captor = ArgumentCaptor.forClass(VerificationReviewProgress.class);
        verify(progressRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRequest()).isSameAs(ref);
        assertThat(captor.getValue().getVerifierId()).isEqualTo(verifierId);
        assertThat(captor.getValue().getCheckedAt()).isNotNull();
        assertThat(captor.getValue().getDocumentValid()).isTrue();
        assertThat(captor.getValue().getViewedAt()).isNull();
    }
}
