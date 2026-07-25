package com.shaopc.worthit.auth.app.security.sametoken;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SameTokenRotationSchedulerTest {

    @Test
    void skipsLockWhenRemainingTtlIsAboveRefreshThreshold() {
        FakeGateway gateway = new FakeGateway(61);
        FakeLock lock = new FakeLock(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SameTokenRotationScheduler scheduler =
                scheduler(true, gateway, lock, registry);

        scheduler.rotateIfNeeded();

        assertThat(lock.attempts).hasValue(0);
        assertThat(gateway.refreshes).hasValue(0);
        assertMetric(registry, "skipped", 1);
        assertThat(registry.get("worthit.security.same-token.remaining")
                .gauge().value()).isEqualTo(61);
    }

    @Test
    void refreshesOnceWhenTtlIsLowAndLockIsAcquired() {
        FakeGateway gateway = new FakeGateway(30);
        FakeLock lock = new FakeLock(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SameTokenRotationScheduler scheduler =
                scheduler(true, gateway, lock, registry);

        scheduler.rotateIfNeeded();

        assertThat(lock.attempts).hasValue(1);
        assertThat(gateway.refreshes).hasValue(1);
        assertMetric(registry, "success", 1);
    }

    @Test
    void skipsRefreshWhenLockIsNotAcquired() {
        FakeGateway gateway = new FakeGateway(30);
        FakeLock lock = new FakeLock(false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SameTokenRotationScheduler scheduler =
                scheduler(true, gateway, lock, registry);

        scheduler.rotateIfNeeded();

        assertThat(lock.attempts).hasValue(1);
        assertThat(gateway.refreshes).hasValue(0);
        assertMetric(registry, "skipped", 1);
    }

    @Test
    void recordsFailureWithoutPropagatingRefreshException() {
        SameTokenRotationGateway gateway = new SameTokenRotationGateway() {
            @Override
            public long remainingSeconds() {
                return 30;
            }

            @Override
            public void refresh() {
                throw new IllegalStateException("sensitive-value-must-not-be-logged");
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SameTokenRotationScheduler scheduler =
                scheduler(true, gateway, new FakeLock(true), registry);

        scheduler.rotateIfNeeded();

        assertMetric(registry, "failure", 1);
    }

    @Test
    void disabledSchedulerDoesNotReadRedisOrAttemptLock() {
        FakeGateway gateway = new FakeGateway(30);
        FakeLock lock = new FakeLock(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SameTokenRotationScheduler scheduler =
                scheduler(false, gateway, lock, registry);

        scheduler.rotateIfNeeded();

        assertThat(gateway.remainingReads).hasValue(0);
        assertThat(gateway.refreshes).hasValue(0);
        assertThat(lock.attempts).hasValue(0);
    }

    private SameTokenRotationScheduler scheduler(
            boolean enabled,
            SameTokenRotationGateway gateway,
            RedisLeaderLock lock,
            SimpleMeterRegistry registry) {
        SameTokenRotationProperties properties = new SameTokenRotationProperties(
                enabled,
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofSeconds(5));
        return new SameTokenRotationScheduler(gateway, lock, properties, registry);
    }

    private void assertMetric(
            SimpleMeterRegistry registry, String result, double expected) {
        assertThat(registry.get("worthit.security.same-token.rotation")
                .tag("result", result)
                .counter()
                .count()).isEqualTo(expected);
    }

    private static final class FakeGateway implements SameTokenRotationGateway {

        private final long remainingSeconds;
        private final AtomicInteger remainingReads = new AtomicInteger();
        private final AtomicInteger refreshes = new AtomicInteger();

        private FakeGateway(long remainingSeconds) {
            this.remainingSeconds = remainingSeconds;
        }

        @Override
        public long remainingSeconds() {
            remainingReads.incrementAndGet();
            return remainingSeconds;
        }

        @Override
        public void refresh() {
            refreshes.incrementAndGet();
        }
    }

    private static final class FakeLock implements RedisLeaderLock {

        private final boolean leader;
        private final AtomicInteger attempts = new AtomicInteger();

        private FakeLock(boolean leader) {
            this.leader = leader;
        }

        @Override
        public boolean executeIfLeader(
                String lockName, Duration ttl, Runnable action) {
            attempts.incrementAndGet();
            if (leader) {
                action.run();
            }
            return leader;
        }
    }
}
