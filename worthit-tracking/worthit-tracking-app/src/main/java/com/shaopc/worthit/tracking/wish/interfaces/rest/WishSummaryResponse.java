package com.shaopc.worthit.tracking.wish.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 想买列表摘要响应。 */
public record WishSummaryResponse(
        String id,
        String name,
        String categoryName,
        String expectedPrice,
        String planDailyCostDisplay,
        boolean residualUnset,
        LocalDate watchDeadline,
        String status,
        long version,
        LocalDateTime createTime) {
}
