package com.shaopc.worthit.tracking.subscription.application;

/**
 * 仅含订阅标识和乐观锁版本的幂等摘要命令。
 */
public record SubscriptionVersionCommand(
        long subscriptionId,
        long version) {
}
