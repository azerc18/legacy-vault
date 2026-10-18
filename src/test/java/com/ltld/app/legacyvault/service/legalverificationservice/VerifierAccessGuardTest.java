package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.exception.NotAssignedVerifierException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.repository.LegalVerificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VerifierAccessGuardTest {

    @Mock
    private LegalVerificationRequestRepository requestRepository;

    @InjectMocks
    private VerifierAccessGuard guard;

    private UUID requestId;
    private UUID verifierId;
    private UUID ownerId;
    private UUID executorId;
    private LegalVerificationRequest request;

    @BeforeEach
    void setUp() {
        requestId = UUID.randomUUID();
        verifierId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        executorId = UUID.randomUUID();

        request = LegalVerificationRequest.builder()
                .id(requestId)
                .owner(User.builder().id(ownerId).build())
                .executor(User.builder().id(executorId).build())
                .status(LegalVerificationStatus.PENDING)
                .build();
    }

    @Test
    void requestNotFound_throwsNotFound() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireReadable(requestId, verifierId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    @Test
    void verifierIsOwner_throwsNotFound() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> guard.requireReadable(requestId, ownerId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    @Test
    void verifierIsExecutor_throwsNotFound() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> guard.requireReadable(requestId, executorId))
                .isInstanceOf(VerificationRequestNotFoundException.class);
    }

    @Test
    void pending_anyNonConflictingVerifier_isAllowed() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThat(guard.requireReadable(requestId, verifierId)).isSameAs(request);
    }

    @Test
    void approvedByAnotherVerifier_throwsNotAssigned() {
        request.setStatus(LegalVerificationStatus.APPROVED);
        request.setVerifier(User.builder().id(UUID.randomUUID()).build());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> guard.requireReadable(requestId, verifierId))
                .isInstanceOf(NotAssignedVerifierException.class);
    }

    @Test
    void approvedByThisVerifier_isAllowed() {
        request.setStatus(LegalVerificationStatus.APPROVED);
        request.setVerifier(User.builder().id(verifierId).build());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThat(guard.requireReadable(requestId, verifierId)).isSameAs(request);
    }
}