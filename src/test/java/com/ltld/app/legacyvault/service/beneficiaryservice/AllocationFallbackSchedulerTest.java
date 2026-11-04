package com.ltld.app.legacyvault.service.beneficiaryservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationFallbackSchedulerTest {

    @Mock private AllocationTimeoutFallbackService service;

    @Test
    void oneVaultFailing_doesNotStopTheOthers() {
        LocalDateTime now = LocalDateTime.now();
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(service.findDueVaultIds(now)).thenReturn(List.of(bad, good));
        when(service.applyFallback(bad, now)).thenThrow(new RuntimeException("db down"));
        when(service.applyFallback(good, now)).thenReturn(2);

        int total = new AllocationFallbackScheduler(service).run(now);

        assertThat(total).isEqualTo(2);
    }
}