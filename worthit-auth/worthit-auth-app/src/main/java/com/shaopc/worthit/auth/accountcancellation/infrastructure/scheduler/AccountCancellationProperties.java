package com.shaopc.worthit.auth.accountcancellation.infrastructure.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/** Auth 账号注销执行调度参数。 */
@ConfigurationProperties("worthit.account-cancellation")
public record AccountCancellationProperties(
        boolean enabled,
        @DefaultValue("30s") Duration checkInterval,
        @DefaultValue("25s") Duration lockTtl,
        @DefaultValue("20") int batchSize,
        @DefaultValue("100") int cleanupBatchSize,
        @DefaultValue("90d") Duration auditRetention) {

    public AccountCancellationProperties {
        requirePositive(checkInterval, "账号注销检查间隔");
        requirePositive(lockTtl, "账号注销锁存活时间");
        requirePositive(auditRetention, "账号注销审计保留期");
        if (batchSize <= 0 || batchSize > 100) {
            throw new IllegalArgumentException("账号注销批量必须在 1..100");
        }
        if (cleanupBatchSize <= 0 || cleanupBatchSize > 1000) {
            throw new IllegalArgumentException("账号注销清理批量必须在 1..1000");
        }
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须大于零");
        }
    }
}
