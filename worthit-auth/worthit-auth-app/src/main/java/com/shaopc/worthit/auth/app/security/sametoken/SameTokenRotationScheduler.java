package com.shaopc.worthit.auth.app.security.sametoken;

import cn.dev33.satoken.dao.SaTokenDao;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 由 Auth 单独承担的 Same-Token 定时轮换调度器。
 */
public final class SameTokenRotationScheduler {

    private static final String ROTATION_LOCK =
            "worthit:security:same-token:rotation";
    private static final String ROTATION_METRIC =
            "worthit.security.same-token.rotation";
    private static final String REMAINING_METRIC =
            "worthit.security.same-token.remaining";

    private final SameTokenRotationGateway gateway;
    private final RedisLeaderLock leaderLock;
    private final SameTokenRotationProperties properties;
    private final Counter success;
    private final Counter skipped;
    private final Counter failure;
    private final AtomicLong remainingSeconds =
            new AtomicLong(SaTokenDao.NOT_VALUE_EXPIRE);

    /**
     * 创建轮换调度器并注册结果与剩余 TTL 指标。
     *
     * @param gateway Sa-Token 轮换能力
     * @param leaderLock Redis 主节点锁
     * @param properties 轮换参数
     * @param meterRegistry 指标注册器
     */
    public SameTokenRotationScheduler(
            SameTokenRotationGateway gateway,
            RedisLeaderLock leaderLock,
            SameTokenRotationProperties properties,
            MeterRegistry meterRegistry) {
        this.gateway = Objects.requireNonNull(gateway, "轮换网关不能为空");
        this.leaderLock = Objects.requireNonNull(leaderLock, "主节点锁不能为空");
        this.properties = Objects.requireNonNull(properties, "轮换参数不能为空");
        Objects.requireNonNull(meterRegistry, "指标注册器不能为空");
        this.success = resultCounter(meterRegistry, "success");
        this.skipped = resultCounter(meterRegistry, "skipped");
        this.failure = resultCounter(meterRegistry, "failure");
        Gauge.builder(REMAINING_METRIC, remainingSeconds, AtomicLong::get)
                .description("Same-Token 剩余有效期秒数")
                .register(meterRegistry);
    }

    /**
     * 检查剩余 TTL，并在取得 Redis 锁后执行一次刷新。
     */
    @Scheduled(
            fixedDelayString =
                    "${worthit.security.same-token.rotation.check-interval:30s}")
    public void rotateIfNeeded() {
        if (!properties.enabled()) {
            return;
        }

        try {
            long observedRemaining = observeRemainingSeconds();
            if (!needsRefresh(observedRemaining)) {
                skipped.increment();
                return;
            }

            boolean leader = leaderLock.executeIfLeader(
                    ROTATION_LOCK, properties.lockTtl(), this::refreshWhileLeader);
            if (!leader) {
                skipped.increment();
            }
        } catch (RuntimeException exception) {
            failure.increment();
        }
    }

    private void refreshWhileLeader() {
        long lockedRemaining = observeRemainingSeconds();
        if (!needsRefresh(lockedRemaining)) {
            skipped.increment();
            return;
        }
        gateway.refresh();
        success.increment();
    }

    private long observeRemainingSeconds() {
        long remaining = gateway.remainingSeconds();
        remainingSeconds.set(remaining);
        return remaining;
    }

    private boolean needsRefresh(long remaining) {
        return remaining != SaTokenDao.NEVER_EXPIRE
                && remaining <= properties.refreshBefore().toSeconds();
    }

    private static Counter resultCounter(MeterRegistry registry, String result) {
        return Counter.builder(ROTATION_METRIC)
                .description("Same-Token 轮换调度结果")
                .tag("result", result)
                .register(registry);
    }
}
