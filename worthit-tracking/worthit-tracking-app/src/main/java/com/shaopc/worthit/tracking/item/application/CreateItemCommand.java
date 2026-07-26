package com.shaopc.worthit.tracking.item.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新建物品用例命令。
 */
public record CreateItemCommand(
        String name,
        Long categoryId,
        BigDecimal purchasePrice,
        BigDecimal expectedYears,
        BigDecimal residualValue,
        LocalDate purchaseDate,
        LocalDate warrantyExpireDate,
        Boolean warrantyReminderEnabled,
        String brandModel,
        String remark) {
}
