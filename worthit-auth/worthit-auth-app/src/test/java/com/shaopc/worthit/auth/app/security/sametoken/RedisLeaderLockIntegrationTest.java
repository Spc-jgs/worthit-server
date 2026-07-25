package com.shaopc.worthit.auth.app.security.sametoken;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisLeaderLockIntegrationTest {

    private static final String LOCK_NAME =
            "worthit:security:same-token:rotation:test";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void createRedisClient() {
        connectionFactory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisClient() {
        connectionFactory.destroy();
    }

    @Test
    void twoSchedulersRefreshOnlyOnce() throws Exception {
        ConcurrentGateway gateway = new ConcurrentGateway();
        SameTokenRotationProperties properties = new SameTokenRotationProperties(
                true,
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofSeconds(5));
        SameTokenRotationScheduler first = new SameTokenRotationScheduler(
                gateway,
                new RedisTemplateLeaderLock(redis),
                properties,
                new SimpleMeterRegistry());
        SameTokenRotationScheduler second = new SameTokenRotationScheduler(
                gateway,
                new RedisTemplateLeaderLock(redis),
                properties,
                new SimpleMeterRegistry());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Void>> futures = List.of(
                    executor.submit(rotateWhenStarted(first, ready, start)),
                    executor.submit(rotateWhenStarted(second, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(gateway.refreshes).hasValue(1);
    }

    @Test
    void lockHasTtlWhileActionRunsAndReleasesAfterward() {
        RedisTemplateLeaderLock lock = new RedisTemplateLeaderLock(redis);
        AtomicLong observedTtl = new AtomicLong();

        boolean executed = lock.executeIfLeader(
                LOCK_NAME,
                Duration.ofSeconds(5),
                () -> observedTtl.set(redis.getExpire(LOCK_NAME, TimeUnit.SECONDS)));

        assertThat(executed).isTrue();
        assertThat(observedTtl).hasPositiveValue();
        assertThat(redis.hasKey(LOCK_NAME)).isFalse();
    }

    @Test
    void ownerMismatchCannotDeleteLock() {
        RedisTemplateLeaderLock lock = new RedisTemplateLeaderLock(redis);
        redis.opsForValue().set(LOCK_NAME, "another-owner", Duration.ofSeconds(5));

        boolean released = lock.releaseIfOwner(LOCK_NAME, "not-the-owner");

        assertThat(released).isFalse();
        assertThat(redis.opsForValue().get(LOCK_NAME)).isEqualTo("another-owner");
        redis.delete(LOCK_NAME);
    }

    @Test
    void actionFailureReleasesOwnedLock() {
        RedisTemplateLeaderLock lock = new RedisTemplateLeaderLock(redis);

        assertThatThrownBy(() -> lock.executeIfLeader(
                LOCK_NAME,
                Duration.ofSeconds(5),
                () -> {
                    throw new IllegalStateException("boom");
                })).isInstanceOf(IllegalStateException.class);

        assertThat(redis.hasKey(LOCK_NAME)).isFalse();
    }

    private Callable<Void> rotateWhenStarted(
            SameTokenRotationScheduler scheduler,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.rotateIfNeeded();
            return null;
        };
    }

    private static final class ConcurrentGateway implements SameTokenRotationGateway {

        private final AtomicLong remainingSeconds = new AtomicLong(0);
        private final AtomicInteger refreshes = new AtomicInteger();

        @Override
        public long remainingSeconds() {
            return remainingSeconds.get();
        }

        @Override
        public void refresh() {
            refreshes.incrementAndGet();
            remainingSeconds.set(3600);
        }
    }
}
