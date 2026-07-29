package com.shaopc.worthit.tracking.wish.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 更新想买命令。 */
public record UpdateWishCommand(
        long version,
        String name,
        Long categoryId,
        BigDecimal expectedPrice,
        BigDecimal expectedYears,
        BigDecimal residualValue,
        String reason,
        String remark,
        LocalDate watchDeadline,
        boolean watchReminderEnabled) {
}
