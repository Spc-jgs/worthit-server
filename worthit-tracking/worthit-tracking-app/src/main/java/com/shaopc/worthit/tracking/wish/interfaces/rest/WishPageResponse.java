package com.shaopc.worthit.tracking.wish.interfaces.rest;

import java.util.List;

/** 想买分页响应。 */
public record WishPageResponse(
        List<WishSummaryResponse> items,
        int page,
        int size,
        long total,
        boolean hasMore) {
}
