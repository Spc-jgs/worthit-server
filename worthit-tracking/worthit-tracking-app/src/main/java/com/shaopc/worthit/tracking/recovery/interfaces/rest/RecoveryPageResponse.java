package com.shaopc.worthit.tracking.recovery.interfaces.rest;

import java.util.List;

/**
 * 已删除资源分页响应。
 */
public record RecoveryPageResponse(
        List<DeletedRecoveryResourceResponse> items,
        int page,
        int size,
        long total,
        boolean hasMore) {
}
