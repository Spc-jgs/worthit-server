package com.shaopc.worthit.tracking.subscription.domain;

/**
 * 包含逻辑删除信息的订阅状态。
 */
public record SubscriptionDeletionState(
        Subscription subscription,
        boolean deleted) {
}
