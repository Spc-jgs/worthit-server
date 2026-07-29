package com.shaopc.worthit.tracking.wish.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 想买聚合事实。
 */
public record Wish(
        long id,
        long userId,
        long categoryId,
        String name,
        BigDecimal expectedPrice,
        BigDecimal expectedYears,
        BigDecimal residualValue,
        String reason,
        String remark,
        LocalDate watchDeadline,
        boolean watchReminderEnabled,
        WishStatus status,
        String lastAbandonReason,
        LocalDateTime lastAbandonAt,
        Long convertedItemId,
        String conversionKey,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
