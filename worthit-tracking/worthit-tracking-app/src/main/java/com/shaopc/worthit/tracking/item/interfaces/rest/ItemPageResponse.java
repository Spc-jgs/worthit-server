package com.shaopc.worthit.tracking.item.interfaces.rest;

import java.util.List;

/**
 * Item 公网分页响应。
 */
public record ItemPageResponse(
        List<ItemSummaryResponse> items,
        int page,
        int size,
        long total,
        boolean hasMore) {
}
