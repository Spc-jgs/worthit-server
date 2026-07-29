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
        String status,
        String lastAbandonReason,
        LocalDateTime lastAbandonAt,
        Long convertedItemId,
        String conversionKey,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    /** 正在考虑。 */
    public static final String CONSIDERING = "CONSIDERING";
    /** 已购买并转换为物品。 */
    public static final String PURCHASED = "PURCHASED";
    /** 已放弃。 */
    public static final String ABANDONED = "ABANDONED";
}
