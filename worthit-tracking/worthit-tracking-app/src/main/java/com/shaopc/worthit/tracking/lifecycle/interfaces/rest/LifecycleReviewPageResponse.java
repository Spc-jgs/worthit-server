package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import java.util.List;

/**
 * 生命周期复盘公网分页响应。
 */
public record LifecycleReviewPageResponse(
        List<LifecycleReviewEntryResponse> items,
        int page,
        int size,
        long total,
        boolean hasMore) {
}
