package com.shaopc.worthit.tracking.wish.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 新建想买命令。 */
public record CreateWishCommand(
        String name,
        Long categoryId,
        BigDecimal expectedPrice,
        BigDecimal expectedYears,
        BigDecimal residualValue,
        String reason,
        String remark,
        LocalDate watchDeadline,
        Boolean watchReminderEnabled) {
}
