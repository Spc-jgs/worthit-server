package com.shaopc.worthit.tracking.item.interfaces.rest;

import java.time.LocalDateTime;

/**
 * 物品列表摘要响应。
 */
public record ItemSummaryResponse(
        String id,
        String name,
        String categoryName,
        String planDailyCostDisplay,
        boolean residualUnset,
        String lifecycleStatus,
        LocalDateTime createTime) {
}
