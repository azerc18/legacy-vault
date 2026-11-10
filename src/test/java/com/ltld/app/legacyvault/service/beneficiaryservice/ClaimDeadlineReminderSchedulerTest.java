package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimDeadlineReminderSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 9, 0);

    @Mock private VaultBeneficiaryRepository repository;
    @Mock private EmailSender emailSender;
    // Chuông thông báo: service ghi thêm thông báo trong app
    @Mock private com.ltld.app.legacyvault.service.notificationservice.InAppNotifier inAppNotifier;
    @InjectMocks private ClaimDeadlineReminderScheduler scheduler;

    private VaultBeneficiary beneficiary(String email, String ownerName, LocalDateTime deadline) {
        User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").fullName(ownerName).build();
        Vault vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Kho").claimDeadlineAt(deadline).build();
        return VaultBeneficiary.builder().id(UUID.randomUUID()).vault(vault).email(email).fullName("An").build();
    }

    @Test
    void sendDue_queriesOneDayWindowForEachMilestone_andPassesDaysLeft() {
        VaultBeneficiary week = beneficiary("a@example.com", "Chu", NOW.plusDays(7));
        VaultBeneficiary day = beneficiary("b@example.com", "Chu", NOW.plusHours(20));
        when(repository.findUnclaimedWithDeadlineBetween(NOW.plusDays(6), NOW.plusDays(7))).thenReturn(List.of(week));
        when(repository.findUnclaimedWithDeadlineBetween(NOW, NOW.plusDays(1))).thenReturn(List.of(day));

        scheduler.sendDue(NOW);

        verify(emailSender).sendClaimDeadlineReminder("a@example.com", "An", "Chu", "Kho",
                week.getVault().getClaimDeadlineAt(), 7, week.getVault().getId());
        verify(emailSender).sendClaimDeadlineReminder("b@example.com", "An", "Chu", "Kho",
                day.getVault().getClaimDeadlineAt(), 1, day.getVault().getId());
    }

    @Test
    void sendDue_ownerWithoutName_usesOwnerEmail() {
        VaultBeneficiary b = beneficiary("a@example.com", " ", NOW.plusDays(7));
        when(repository.findUnclaimedWithDeadlineBetween(any(), any())).thenReturn(List.of());
        when(repository.findUnclaimedWithDeadlineBetween(NOW.plusDays(6), NOW.plusDays(7))).thenReturn(List.of(b));

        scheduler.sendDue(NOW);

        verify(emailSender).sendClaimDeadlineReminder(eq("a@example.com"), eq("An"), eq("owner@example.com"), eq("Kho"),
                any(), eq(7), any());
    }

    @Test
    void sendDue_oneMailFails_stillSendsTheRest() {
        VaultBeneficiary bad = beneficiary("bad@example.com", "Chu", NOW.plusDays(7));
        VaultBeneficiary good = beneficiary("good@example.com", "Chu", NOW.plusDays(7));
        when(repository.findUnclaimedWithDeadlineBetween(any(), any())).thenReturn(List.of());
        when(repository.findUnclaimedWithDeadlineBetween(NOW.plusDays(6), NOW.plusDays(7))).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("smtp")).when(emailSender).sendClaimDeadlineReminder(eq("bad@example.com"),
                any(), any(), any(), any(), anyInt(), any());

        scheduler.sendDue(NOW);

        verify(emailSender).sendClaimDeadlineReminder(eq("good@example.com"), any(), any(), any(), any(), eq(7), any());
    }

    @Test
    void sendDue_nothingDue_sendsNothing() {
        when(repository.findUnclaimedWithDeadlineBetween(any(), any())).thenReturn(List.of());

        scheduler.sendDue(NOW);

        verify(emailSender, never()).sendClaimDeadlineReminder(any(), any(), any(), any(), any(), anyInt(), any());
    }
}
