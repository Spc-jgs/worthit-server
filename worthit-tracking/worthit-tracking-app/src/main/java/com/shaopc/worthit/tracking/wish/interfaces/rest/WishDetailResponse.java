package com.shaopc.worthit.tracking.wish.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 想买详情响应。 */
public record WishDetailResponse(
        String id,
        String name,
        String categoryId,
        String categoryName,
        String expectedPrice,
        String expectedYears,
        String residualValue,
        boolean residualUnset,
        String reason,
        String remark,
        LocalDate watchDeadline,
        boolean watchReminderEnabled,
        String status,
        String lastAbandonReason,
        LocalDateTime lastAbandonAt,
        String convertedItemId,
        int expectedUseDays,
        String planDailyCost,
        String planDailyCostDisplay,
        boolean planDailyCostTiny,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
