package com.shaopc.worthit.tracking.subscription.domain;

/**
 * 订阅详情及分类展示名。
 */
public record SubscriptionWithCategory(
        Subscription subscription,
        String categoryName) {
}
