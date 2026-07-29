package com.shaopc.worthit.tracking.wish.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 想买列表摘要。 */
public record WishSummary(
        long id,
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
