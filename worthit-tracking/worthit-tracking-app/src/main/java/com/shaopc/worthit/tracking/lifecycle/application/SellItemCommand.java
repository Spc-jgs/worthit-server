package com.shaopc.worthit.tracking.lifecycle.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 卖出用例命令。
 */
public record SellItemCommand(
        long version,
        LocalDate saleDate,
        BigDecimal saleAmount,
        String remark) {
}
