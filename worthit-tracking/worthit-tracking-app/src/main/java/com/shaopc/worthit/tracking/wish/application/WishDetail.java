package com.shaopc.worthit.tracking.wish.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 想买详情应用结果。 */
public record WishDetail(
        long id,
        String name,
        long categoryId,
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
        Long convertedItemId,
        int expectedUseDays,
        String planDailyCost,
        String planDailyCostDisplay,
        boolean planDailyCostTiny,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
