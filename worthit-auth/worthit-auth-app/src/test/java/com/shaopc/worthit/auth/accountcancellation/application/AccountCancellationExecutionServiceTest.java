package com.shaopc.worthit.auth.accountcancellation.application;

import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler.AccountCancellationProperties;
import com.shaopc.worthit.reminder.client.api.ReminderAccountCancellationClient;
import com.shaopc.worthit.reminder.client.response.ReminderAccountCancellationResponse;
import com.shaopc.worthit.tracking.client.api.TrackingAccountCancellationClient;
import com.shaopc.worthit.tracking.client.response.TrackingAccountCancellationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountCancellationExecutionServiceTest {

    @Test
    void retriesBothDownstreamsAndFinalizesOnlyAfterBothConfirm() {
        AccountCancellationStore store = mock(AccountCancellationStore.class);
        AccountCancellationExecutionTransactions transactions =
                mock(AccountCancellationExecutionTransactions.class);
        TrackingAccountCancellationClient tracking =
                mock(TrackingAccountCancellationClient.class);
        ReminderAccountCancellationClient reminder =
                mock(ReminderAccountCancellationClient.class);
        AccountCancellation executing = executing();

        when(store.findExecutable(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(executing));
        when(transactions.claim(eq(executing), any(LocalDateTime.class)))
                .thenReturn(Optional.of(executing));
        when(tracking.cancelAccount(anyLong(), any(), any()))
                .thenReturn(new TrackingAccountCancellationResponse(true));
        when(reminder.cancelAccount(anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("temporary"))
                .thenReturn(new ReminderAccountCancellationResponse(true));

        AccountCancellationExecutionService service =
                new AccountCancellationExecutionServiceImpl(
                        store,
                        transactions,
                        tracking,
                        reminder,
                        properties(),
                        Clock.fixed(
                                Instant.parse("2026-08-16T04:00:00Z"),
                                ZoneId.of("Asia/Shanghai")),
                        new SimpleMeterRegistry());

        service.processBatch();
        service.processBatch();

        verify(tracking, times(2)).cancelAccount(
                eq(1001L), eq("9001"), any());
        verify(reminder, times(2)).cancelAccount(
                eq(1001L), eq("9001"), any());
        verify(transactions, times(1)).finalizeExecution(
                eq(executing), any(LocalDateTime.class));
    }

    private static AccountCancellation executing() {
        LocalDateTime applyAt = LocalDateTime.of(2026, 8, 9, 12, 0);
        return new AccountCancellation(
                9001L,
                1001L,
                applyAt,
                applyAt.plusDays(7),
                null,
                AccountCancellationStatus.EXECUTING,
                null,
                2L);
    }

    private static AccountCancellationProperties properties() {
        return new AccountCancellationProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(25),
                20,
                100,
                Duration.ofDays(90));
    }
}
