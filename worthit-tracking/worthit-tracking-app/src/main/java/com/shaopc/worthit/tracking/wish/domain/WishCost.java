package com.shaopc.worthit.tracking.wish.domain;

import java.math.BigDecimal;

/**
 * 想买计划日均派生结果。
 */
public record WishCost(
        int expectedUseDays,
        BigDecimal exactPlanDailyCost,
        BigDecimal planDailyCost,
        String planDailyCostDisplay,
        boolean planDailyCostTiny,
        boolean residualUnset) {
}
