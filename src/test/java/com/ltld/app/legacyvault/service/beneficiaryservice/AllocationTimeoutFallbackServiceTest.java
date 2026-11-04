package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.AssetsReassignedEvent;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationTimeoutFallbackServiceTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private BeneficiaryClaimRepository claimRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AllocationTimeoutFallbackService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 20, 10, 0);
    private UUID vaultId;
    private Vault vault;
    private VaultBeneficiary holder;
    private VaultBeneficiary receiver;

    @BeforeEach
    void setUp() {
        service = new AllocationTimeoutFallbackService(vaultRepository, assetAllocationRepository,
                claimRepository, auditLogService, eventPublisher, 30);
        vaultId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setFullName("Bình");
        vault = new Vault();
        vault.setId(vaultId);
        vault.setName("Gia đình");
        vault.setOwner(owner);
        vault.setStatus(VaultStatus.UNLOCKED);
        vault.setUnlockedAt(now.minusDays(31));
        vault.setClaimDeadlineAt(now.plusDays(29));
        holder = row(BeneficiaryInviteStatus.INVITED);
        receiver = row(BeneficiaryInviteStatus.ACCEPTED);
    }

    private VaultBeneficiary row(BeneficiaryInviteStatus status) {
        User u = new User();
        u.setId(UUID.randomUUID());
        return VaultBeneficiary.builder().id(UUID.randomUUID()).vault(vault).user(u)
                .email(UUID.randomUUID() + "@example.com").status(status).build();
    }

    private AssetAllocation allocation(String assetName) {
        DigitalAsset asset = DigitalAsset.builder().id(UUID.randomUUID()).vault(vault)
                .assetType(AssetType.BANK_ACCOUNT).assetName(assetName).status(AssetStatus.ACTIVE).build();
        return AssetAllocation.builder().id(UUID.randomUUID()).asset(asset).beneficiary(holder)
                .contingentBeneficiary(receiver).mode(AllocationMode.EXCLUSIVE).build();
    }

    private BeneficiaryClaim claim(VaultBeneficiary vb, ClaimStatus status) {
        return BeneficiaryClaim.builder().id(UUID.randomUUID()).vault(vault).beneficiary(vb.getUser())
                .status(status).claimDeadlineAt(now.plusDays(29)).build();
    }

    private void stubVaultAndCandidates(AssetAllocation... allocations) {
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(vault));
        when(assetAllocationRepository.findTimeoutFallbackCandidates(eq(vaultId), any()))
                .thenReturn(List.of(allocations));
    }

    // lenient: service tra claim của cả người giữ chính lẫn người nhận; người nào không stub thì
    // mặc định trả Optional.empty() (chưa có claim)
    private void stubClaim(VaultBeneficiary vb, BeneficiaryClaim claim) {
        lenient().when(claimRepository.findByVaultIdAndBeneficiaryId(vaultId, vb.getUser().getId()))
                .thenReturn(Optional.of(claim));
    }

    @Test
    void silentHolder_activeReceiver_isReassigned_auditedAndEventPublished() {
        AssetAllocation al = allocation("Vietcombank");
        stubVaultAndCandidates(al);
        stubClaim(receiver, claim(receiver, ClaimStatus.PENDING));

        int result = service.applyFallback(vaultId, now);

        assertThat(result).isEqualTo(1);
        assertThat(al.getEffectiveBeneficiary()).isSameAs(receiver);
        verify(assetAllocationRepository).save(al);
        verify(auditLogService).log(eq(AuditAction.ASSET_ALLOCATION_FALLBACK), eq(AuditResult.SUCCESS),
                isNull(), isNull(), eq(vaultId), eq("Asset"), eq(al.getAsset().getId().toString()),
                contains("reason=NO_RESPONSE"));
        ArgumentCaptor<AssetsReassignedEvent> captor = ArgumentCaptor.forClass(AssetsReassignedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AssetsReassignedEvent event = captor.getValue();
        assertThat(event.reason()).isEqualTo(AssetsReassignedEvent.Reason.NO_RESPONSE);
        assertThat(event.ownerName()).isEqualTo("Bình");
        assertThat(event.receivers()).hasSize(1);
        assertThat(event.receivers().get(0).userId()).isEqualTo(receiver.getUser().getId());
        assertThat(event.receivers().get(0).assetCount()).isEqualTo(1);
    }

    @Test
    void twoAssetsToSameReceiver_publishOneReceiverWithTotalCount() {
        AssetAllocation al1 = allocation("Vietcombank");
        AssetAllocation al2 = allocation("Gmail");
        stubVaultAndCandidates(al1, al2);
        stubClaim(receiver, claim(receiver, ClaimStatus.PENDING));

        assertThat(service.applyFallback(vaultId, now)).isEqualTo(2);

        ArgumentCaptor<AssetsReassignedEvent> captor = ArgumentCaptor.forClass(AssetsReassignedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().receivers()).hasSize(1);
        assertThat(captor.getValue().receivers().get(0).assetCount()).isEqualTo(2);
    }

    @Test
    void holderAlreadyStartedClaim_isSkipped() {
        AssetAllocation al = allocation("Vietcombank");
        stubVaultAndCandidates(al);
        stubClaim(holder, claim(holder, ClaimStatus.PENDING));

        assertThat(service.applyFallback(vaultId, now)).isZero();

        assertThat(al.getEffectiveBeneficiary()).isNull();
        verify(assetAllocationRepository, never()).save(any(AssetAllocation.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void receiverWithoutClaim_isSkipped() {
        AssetAllocation al = allocation("Vietcombank");
        stubVaultAndCandidates(al);   // receiver chưa có claim: chưa hoạt động

        assertThat(service.applyFallback(vaultId, now)).isZero();

        assertThat(al.getEffectiveBeneficiary()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void receiverAlreadyClosedHisShare_isSkipped() {
        AssetAllocation al = allocation("Vietcombank");
        stubVaultAndCandidates(al);
        stubClaim(receiver, claim(receiver, ClaimStatus.CLAIMED));

        assertThat(service.applyFallback(vaultId, now)).isZero();

        assertThat(al.getEffectiveBeneficiary()).isNull();
    }

    @Test
    void vaultNotPastResponseWindow_doesNothing() {
        vault.setUnlockedAt(now.minusDays(10));
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(vault));

        assertThat(service.applyFallback(vaultId, now)).isZero();

        verify(assetAllocationRepository, never()).findTimeoutFallbackCandidates(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void vaultNoLongerUnlocked_doesNothing() {
        vault.setStatus(VaultStatus.ARCHIVED_LOCKED);
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(vault));

        assertThat(service.applyFallback(vaultId, now)).isZero();

        verify(assetAllocationRepository, never()).findTimeoutFallbackCandidates(any(), any());
    }
}