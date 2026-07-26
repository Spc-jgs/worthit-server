package com.shaopc.worthit.tracking.item.application;

import java.time.LocalDateTime;

/**
 * Item 列表用例结果。
 */
public record ItemSummary(
        long id,
        String name,
        String categoryName,
        String planDailyCostDisplay,
        boolean residualUnset,
        String lifecycleStatus,
        LocalDateTime createTime) {
}
