package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import java.util.List;

/**
 * 订阅分页公网响应。
 */
public record SubscriptionPageResponse(
        List<SubscriptionSummaryResponse> items,
        int page,
        int size,
        long total,
        boolean hasMore) {
}
