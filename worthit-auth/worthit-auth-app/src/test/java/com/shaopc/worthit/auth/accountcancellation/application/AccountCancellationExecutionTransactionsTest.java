package com.shaopc.worthit.auth.accountcancellation.application;

import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountCancellationExecutionTransactionsTest {

    private static final long CANCELLATION_ID = 9001L;
    private static final long USER_ID = 1001L;
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 16, 12, 0);

    private final AccountCancellationStore store =
            mock(AccountCancellationStore.class);
    private final AuthUserRepository userRepository =
            mock(AuthUserRepository.class);
    private final UserSession userSession = mock(UserSession.class);
    private final AccountCancellationExecutionTransactions transactions =
            new AccountCancellationExecutionTransactions(
                    store, userRepository, userSession);

    @Test
    void claimsDueCancellationAndRevokesSessionsBeforeReturning() {
        AccountCancellation pending = cancellation(
                AccountCancellationStatus.PENDING, NOW.minusDays(7), 1L);
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(new AuthUser(USER_ID, null, null, true)));
        when(store.findForUpdate(CANCELLATION_ID, USER_ID))
                .thenReturn(Optional.of(pending));
        when(store.markUserExecuting(USER_ID, NOW)).thenReturn(true);
        when(store.claimExecution(CANCELLATION_ID, USER_ID, 1L, NOW))
                .thenReturn(true);

        Optional<AccountCancellation> claimed = transactions.claim(pending, NOW);

        assertThat(claimed)
                .get()
                .extracting(AccountCancellation::status, AccountCancellation::version)
                .containsExactly(AccountCancellationStatus.EXECUTING, 2L);
        InOrder order = inOrder(userRepository, store, userSession);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(store).findForUpdate(CANCELLATION_ID, USER_ID);
        order.verify(store).markUserExecuting(USER_ID, NOW);
        order.verify(store).claimExecution(CANCELLATION_ID, USER_ID, 1L, NOW);
        order.verify(userSession).logoutUser(USER_ID);
    }

    @Test
    void retriesSessionRevocationForAlreadyExecutingCancellation() {
        AccountCancellation executing = cancellation(
                AccountCancellationStatus.EXECUTING, NOW.minusDays(8), 2L);
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(new AuthUser(USER_ID, null, null, false)));
        when(store.findForUpdate(CANCELLATION_ID, USER_ID))
                .thenReturn(Optional.of(executing));

        Optional<AccountCancellation> claimed = transactions.claim(executing, NOW);

        assertThat(claimed).contains(executing);
        verify(userSession).logoutUser(USER_ID);
        verify(store, never()).markUserExecuting(USER_ID, NOW);
        verify(store, never()).claimExecution(
                CANCELLATION_ID, USER_ID, 2L, NOW);
    }

    @Test
    void leavesSessionUntouchedWhenCancellationIsNotDue() {
        AccountCancellation pending = cancellation(
                AccountCancellationStatus.PENDING, NOW.minusDays(6), 1L);
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(new AuthUser(USER_ID, null, null, true)));
        when(store.findForUpdate(CANCELLATION_ID, USER_ID))
                .thenReturn(Optional.of(pending));

        assertThat(transactions.claim(pending, NOW)).isEmpty();

        verify(userSession, never()).logoutUser(USER_ID);
    }

    @Test
    void revokesSessionsAgainImmediatelyBeforeFinalCleanup() {
        AccountCancellation executing = cancellation(
                AccountCancellationStatus.EXECUTING, NOW.minusDays(8), 2L);
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(new AuthUser(USER_ID, null, null, false)));
        when(store.findForUpdate(CANCELLATION_ID, USER_ID))
                .thenReturn(Optional.of(executing));

        transactions.finalizeExecution(executing, NOW);

        InOrder order = inOrder(userRepository, store, userSession);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(store).findForUpdate(CANCELLATION_ID, USER_ID);
        order.verify(userSession).logoutUser(USER_ID);
        order.verify(store).finalizeCancellation(
                CANCELLATION_ID, USER_ID, 2L, NOW);
    }

    private static AccountCancellation cancellation(
            AccountCancellationStatus status,
            LocalDateTime applyAt,
            long version) {
        return new AccountCancellation(
                CANCELLATION_ID,
                USER_ID,
                applyAt,
                applyAt.plusDays(7),
                null,
                status,
                null,
                version);
    }
}
