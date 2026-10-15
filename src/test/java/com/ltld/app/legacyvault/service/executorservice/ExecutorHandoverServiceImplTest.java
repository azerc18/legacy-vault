package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VerificationRequestVault;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorHandoverServiceImplTest {

    private static final String LOGIN = "executor@example.com";

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private VerificationRequestVaultRepository verificationRequestVaultRepository;
    @InjectMocks private ExecutorHandoverServiceImpl service;

    private UUID assignmentId;
    private User owner;
    private User executor;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        owner = User.builder().id(UUID.randomUUID()).fullName("Owner").build();
        executor = User.builder().id(UUID.randomUUID()).email(LOGIN).build();
    }

    private void stubGuardAllows() {
        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId).owner(owner).executorUser(executor).status(ExecutorStatus.ACTIVE).build();
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(assignment);
    }

    private Vault vault(String name, VaultStatus status) {
        return Vault.builder().id(UUID.randomUUID()).owner(owner).name(name).status(status).build();
    }

    private LegalVerificationRequest request(LegalVerificationStatus status) {
        return LegalVerificationRequest.builder()
                .id(UUID.randomUUID()).owner(owner).executor(executor).status(status)
                .createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
    }

    private VerificationRequestVault row(LegalVerificationRequest request, Vault vault) {
        return VerificationRequestVault.builder().id(UUID.randomUUID()).request(request).vault(vault).build();
    }

    private void stubRows(VerificationRequestVault... rows) {
        when(verificationRequestVaultRepository.findProgressRows(executor.getId(), owner.getId()))
                .thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("Active vault with a PENDING request -> PENDING, with the request timestamps")
    void pendingRequest_isPending() {
        stubGuardAllows();
        LegalVerificationRequest request = request(LegalVerificationStatus.PENDING);
        stubRows(row(request, vault("A", VaultStatus.ACTIVE)));

        VaultHandoverProgressResponse res = service.getHandoverProgress(assignmentId, LOGIN).get(0);

        assertEquals(HandoverStatus.PENDING, res.getHandoverStatus());
        assertEquals(request.getId(), res.getRequestId());
        assertEquals(request.getCreatedAt(), res.getRequestedAt());
        assertNull(res.getDecidedAt());
    }

    @Test
    @DisplayName("Active vault with an APPROVED or SIGNED request -> VERIFIED")
    void approvedOrSignedRequest_isVerified() {
        stubGuardAllows();
        stubRows(row(request(LegalVerificationStatus.APPROVED), vault("A", VaultStatus.ACTIVE)),
                row(request(LegalVerificationStatus.SIGNED), vault("B", VaultStatus.ACTIVE)));

        List<VaultHandoverProgressResponse> res = service.getHandoverProgress(assignmentId, LOGIN);

        assertEquals(HandoverStatus.VERIFIED, res.get(0).getHandoverStatus());
        assertEquals(HandoverStatus.VERIFIED, res.get(1).getHandoverStatus());
    }

    @Test
    @DisplayName("Active vault with a REJECTED request -> REJECTED, exposing the rejection reason")
    void rejectedRequest_isRejectedWithReason() {
        stubGuardAllows();
        LegalVerificationRequest request = request(LegalVerificationStatus.REJECTED);
        request.setRejectionReason("Giấy chứng tử không rõ");
        request.setDecidedAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        stubRows(row(request, vault("A", VaultStatus.ACTIVE)));

        VaultHandoverProgressResponse res = service.getHandoverProgress(assignmentId, LOGIN).get(0);

        assertEquals(HandoverStatus.REJECTED, res.getHandoverStatus());
        assertEquals("Giấy chứng tử không rõ", res.getRejectionReason());
        assertEquals(LocalDateTime.of(2026, 10, 2, 10, 0), res.getDecidedAt());
    }

    @Test
    @DisplayName("Vault lifecycle status wins over the request status: UNLOCKED, CLAIMED, ARCHIVED_LOCKED")
    void vaultLifecycleStatus_overridesRequestStatus() {
        stubGuardAllows();
        Vault unlocked = vault("U", VaultStatus.UNLOCKED);
        unlocked.setUnlockedAt(LocalDateTime.of(2026, 10, 3, 8, 0));
        unlocked.setClaimDeadlineAt(LocalDateTime.of(2026, 12, 2, 8, 0));
        stubRows(row(request(LegalVerificationStatus.SIGNED), unlocked),
                row(request(LegalVerificationStatus.SIGNED), vault("C", VaultStatus.CLAIMED)),
                row(request(LegalVerificationStatus.SIGNED), vault("X", VaultStatus.ARCHIVED_LOCKED)));

        List<VaultHandoverProgressResponse> res = service.getHandoverProgress(assignmentId, LOGIN);

        assertEquals(HandoverStatus.UNLOCKED, res.get(0).getHandoverStatus());
        assertEquals(LocalDateTime.of(2026, 10, 3, 8, 0), res.get(0).getUnlockedAt());
        assertEquals(LocalDateTime.of(2026, 12, 2, 8, 0), res.get(0).getClaimDeadlineAt());
        assertEquals(HandoverStatus.CLAIMED, res.get(1).getHandoverStatus());
        assertEquals(HandoverStatus.ARCHIVED_LOCKED, res.get(2).getHandoverStatus());
    }

    @Test
    @DisplayName("A vault in several requests appears once, using its newest request (first row)")
    void vaultInSeveralRequests_usesNewestRequestOnly() {
        stubGuardAllows();
        Vault vault = vault("A", VaultStatus.ACTIVE);
        LegalVerificationRequest newest = request(LegalVerificationStatus.PENDING);
        LegalVerificationRequest older = request(LegalVerificationStatus.REJECTED);
        stubRows(row(newest, vault), row(older, vault));

        List<VaultHandoverProgressResponse> res = service.getHandoverProgress(assignmentId, LOGIN);

        assertEquals(1, res.size());
        assertEquals(HandoverStatus.PENDING, res.get(0).getHandoverStatus());
        assertEquals(newest.getId(), res.get(0).getRequestId());
    }

    @Test
    @DisplayName("Executor has not sent any request -> empty list (SRS 2a)")
    void noRequests_returnsEmptyList() {
        stubGuardAllows();
        when(verificationRequestVaultRepository.findProgressRows(executor.getId(), owner.getId()))
                .thenReturn(Collections.emptyList());

        List<VaultHandoverProgressResponse> res = service.getHandoverProgress(assignmentId, LOGIN);

        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("Rows are looked up for this executor and this owner only")
    void rowsAreLookedUpForCallerAndAssignmentOwner() {
        stubGuardAllows();
        when(verificationRequestVaultRepository.findProgressRows(executor.getId(), owner.getId()))
                .thenReturn(Collections.emptyList());

        service.getHandoverProgress(assignmentId, LOGIN);

        verify(verificationRequestVaultRepository).findProgressRows(executor.getId(), owner.getId());
    }

    @Test
    @DisplayName("Guard rejects with 404 -> no query is made")
    void guardNotFound_propagatesAndSkipsQuery() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        assertThrows(ExecutorAssignmentNotFoundException.class, () -> service.getHandoverProgress(assignmentId, LOGIN));

        verifyNoInteractions(verificationRequestVaultRepository);
    }

    @Test
    @DisplayName("Guard rejects with 403 -> no query is made")
    void guardNotActive_propagatesAndSkipsQuery() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorNotActiveException("Chưa đến thời điểm thực hiện quyền"));

        assertThrows(ExecutorNotActiveException.class, () -> service.getHandoverProgress(assignmentId, LOGIN));

        verifyNoInteractions(verificationRequestVaultRepository);
    }

    @Test
    @DisplayName("Response DTO must not declare contact data or secrets")
    void responseDto_mustNotContainSensitiveFields() {
        for (Field field : VaultHandoverProgressResponse.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("email") || name.contains("secret") || name.contains("password")
                            || name.contains("attachment") || name.contains("certificate"),
                    "Sensitive field in DTO: " + field.getName());
        }
    }
}
