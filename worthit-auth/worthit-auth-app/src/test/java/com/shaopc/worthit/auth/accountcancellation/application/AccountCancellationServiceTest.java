package com.shaopc.worthit.auth.accountcancellation.application;

import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthCancellationOperation;
import com.shaopc.worthit.auth.accountcancellation.application.idempotency.AuthIdempotencyExecutor;
import com.shaopc.worthit.auth.accountcancellation.application.port.AccountCancellationStore;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellation;
import com.shaopc.worthit.auth.accountcancellation.domain.AccountCancellationStatus;
import com.shaopc.worthit.auth.accountcancellation.interfaces.rest.AccountCancellationResponse;
import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import com.shaopc.worthit.common.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountCancellationServiceTest {

    private static final long USER_ID = 1001L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-08-09T04:00:00Z");

    private InMemoryStore store;
    private AccountCancellationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        service = serviceAt(NOW);
    }

    @Test
    void appliesSevenDayCoolingPeriodAndDoesNotDelayExistingRequest() {
        AccountCancellationResponse first = service.apply(key());
        AccountCancellationResponse repeated = service.apply(key());

        assertThat(first.id()).isEqualTo(repeated.id());
        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(first.effectiveAt()).isEqualTo(first.applyAt().plusDays(7));
        assertThat(store.created).isEqualTo(1);
    }

    @Test
    void returnsRevokedTerminalStateForAnotherIdempotencyKey() {
        AccountCancellationResponse applied = service.apply(key());
        AccountCancellationResponse revoked = service.revoke(
                key(), applied.id(), applied.version());
        AccountCancellationResponse repeated = service.revoke(
                key(), applied.id(), applied.version());

        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThat(revoked.version()).isEqualTo(2L);
        assertThat(repeated).isEqualTo(revoked);
    }

    @Test
    void rejectsRevocationAtEffectiveBoundary() {
        AccountCancellationResponse applied = service.apply(key());
        service = serviceAt(NOW.plusSeconds(7L * 24 * 60 * 60));

        assertThatThrownBy(() -> service.revoke(
                key(), applied.id(), applied.version()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
    }

    @Test
    void returnsNullStatusBeforeFirstApplication() {
        assertThat(service.status().cancellation()).isNull();
    }

    private AccountCancellationService serviceAt(Instant instant) {
        return new AccountCancellationServiceImpl(
                store,
                new ImmediateExecutor(),
                new SingleUserRepository(),
                new FixedSession(),
                Clock.fixed(instant, ZONE));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static final class ImmediateExecutor
            implements AuthIdempotencyExecutor {

        @Override
        public <T> T execute(
                long userId,
                AuthCancellationOperation operation,
                String idempotencyKey,
                String requestHash,
                Class<T> responseType,
                IdempotentAction<T> action) {
            return action.execute();
        }
    }

    private static final class SingleUserRepository
            implements AuthUserRepository {

        @Override
        public Optional<AuthUser> findByWechatIdentity(
                String appId, String externalSubject) {
            return Optional.empty();
        }

        @Override
        public Optional<AuthUser> findById(long userId) {
            return userId == USER_ID
                    ? Optional.of(new AuthUser(USER_ID, null, null, true))
                    : Optional.empty();
        }

        @Override
        public Optional<AuthUser> findByIdForUpdate(long userId) {
            return findById(userId);
        }

        @Override
        public AuthUser createWechatUser(WechatIdentity identity) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FixedSession implements UserSession {

        @Override
        public IssuedToken login(long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long currentUserId() {
            return USER_ID;
        }

        @Override
        public void logout() {
        }

        @Override
        public void logoutUser(long userId) {
        }
    }

    private static final class InMemoryStore
            implements AccountCancellationStore {

        private final List<AccountCancellation> records = new ArrayList<>();
        private int created;

        @Override
        public Optional<AccountCancellation> findOpenForUpdate(long userId) {
            return records.stream()
                    .filter(value -> value.userId() == userId)
                    .filter(value -> value.status()
                            == AccountCancellationStatus.PENDING
                            || value.status()
                            == AccountCancellationStatus.EXECUTING)
                    .findFirst();
        }

        @Override
        public Optional<AccountCancellation> findLatest(long userId) {
            return records.stream()
                    .filter(value -> value.userId() == userId)
                    .reduce((first, second) -> second);
        }

        @Override
        public Optional<AccountCancellation> findForUpdate(
                long cancellationId, long userId) {
            return records.stream()
                    .filter(value -> value.id() == cancellationId)
                    .filter(value -> value.userId() == userId)
                    .findFirst();
        }

        @Override
        public Optional<AccountCancellation> findById(long cancellationId) {
            return records.stream()
                    .filter(value -> value.id() == cancellationId)
                    .findFirst();
        }

        @Override
        public AccountCancellation create(
                long cancellationId,
                long userId,
                LocalDateTime applyAt,
                LocalDateTime effectiveAt) {
            AccountCancellation value = new AccountCancellation(
                    cancellationId,
                    userId,
                    applyAt,
                    effectiveAt,
                    null,
                    AccountCancellationStatus.PENDING,
                    null,
                    1L);
            records.add(value);
            created++;
            return value;
        }

        @Override
        public boolean revoke(
                long cancellationId,
                long userId,
                long expectedVersion,
                LocalDateTime revokedAt) {
            AccountCancellation current = findForUpdate(cancellationId, userId)
                    .orElseThrow();
            if (current.status() != AccountCancellationStatus.PENDING
                    || current.version() != expectedVersion) {
                return false;
            }
            records.set(records.indexOf(current), new AccountCancellation(
                    current.id(), current.userId(), current.applyAt(),
                    current.effectiveAt(), null,
                    AccountCancellationStatus.REVOKED,
                    revokedAt, current.version() + 1));
            return true;
        }

        @Override
        public List<AccountCancellation> findExecutable(
                LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        public long countByStatus(AccountCancellationStatus status) {
            return records.stream().filter(value -> value.status() == status).count();
        }

        @Override
        public Optional<LocalDateTime> findOldestOpenApplyAt() {
            return records.stream()
                    .filter(value -> value.status() == AccountCancellationStatus.PENDING
                            || value.status() == AccountCancellationStatus.EXECUTING)
                    .map(AccountCancellation::applyAt)
                    .min(LocalDateTime::compareTo);
        }

        @Override
        public boolean claimExecution(
                long cancellationId,
                long userId,
                long expectedVersion,
                LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markUserExecuting(long userId, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finalizeCancellation(
                long cancellationId,
                long userId,
                long expectedVersion,
                LocalDateTime completedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteTerminalBefore(LocalDateTime cutoff, int limit) {
            return 0;
        }
    }
}
