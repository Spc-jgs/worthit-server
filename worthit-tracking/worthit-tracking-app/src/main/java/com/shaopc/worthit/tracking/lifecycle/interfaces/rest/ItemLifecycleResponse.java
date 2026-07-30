package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import java.time.LocalDateTime;

/**
 * 物品处置公网统一响应。
 */
public record ItemLifecycleResponse(
        String itemId,
        String lifecycleStatus,
        ItemDisposalResponse disposal,
        long version,
        LocalDateTime updateTime) {
}
