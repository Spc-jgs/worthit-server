package com.shaopc.worthit.tracking.outbox.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Outbox Relay 批量、租约和退避配置。
 *
 * @param enabled 是否启用定时 Relay
 * @param batchSize 单批最大事件数
 * @param leaseDuration PROCESSING 租约时长
 * @param maxRetries 最大失败次数，达到后进入 DEAD
 * @param initialBackoff 首次失败退避
 * @param maxBackoff 最大退避
 */
@ConfigurationProperties("worthit.outbox.relay")
public record OutboxRelayProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("50") int batchSize,
        @DefaultValue("30s") Duration leaseDuration,
        @DefaultValue("8") int maxRetries,
        @DefaultValue("5s") Duration initialBackoff,
        @DefaultValue("15m") Duration maxBackoff) {

    /**
     * 校验 Relay 参数，避免无界扫描或高频空转。
     */
    public OutboxRelayProperties {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException(
                    "Outbox Relay批大小必须在1至500之间");
        }
        if (leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "Outbox Relay租约必须为正数");
        }
        if (maxRetries < 1 || maxRetries > 30) {
            throw new IllegalArgumentException(
                    "Outbox Relay最大重试次数必须在1至30之间");
        }
        if (initialBackoff == null
                || initialBackoff.isZero()
                || initialBackoff.isNegative()) {
            throw new IllegalArgumentException(
                    "Outbox Relay首次退避必须为正数");
        }
        if (maxBackoff == null
                || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "Outbox Relay最大退避不得小于首次退避");
        }
    }
}
