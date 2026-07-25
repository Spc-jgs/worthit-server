package com.shaopc.worthit.auth.app.security.sametoken;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * Auth 服务的 Same-Token 定时轮换参数。
 *
 * @param enabled 是否启用轮换
 * @param checkInterval 检查间隔
 * @param refreshBefore 剩余时间低于该阈值时刷新
 * @param lockTtl Redis 主节点锁存活时间
 */
@ConfigurationProperties("worthit.security.same-token.rotation")
public record SameTokenRotationProperties(
        boolean enabled,
        @DefaultValue("30s") Duration checkInterval,
        @DefaultValue("5m") Duration refreshBefore,
        @DefaultValue("30s") Duration lockTtl) {

    /**
     * 校验轮换参数，避免零值或负值导致忙循环和无效锁。
     */
    public SameTokenRotationProperties {
        requirePositive(checkInterval, "Same-Token 检查间隔");
        requirePositive(refreshBefore, "Same-Token 刷新阈值");
        requirePositive(lockTtl, "Same-Token 锁存活时间");
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + "不能为空");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "必须大于零");
        }
    }
}
