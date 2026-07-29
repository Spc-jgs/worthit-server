package com.shaopc.worthit.tracking.dashboard.application;

import java.math.BigDecimal;

/**
 * Dashboard 当前考虑中想买的数据库精确汇总。
 *
 * @param count 想买数量
 * @param amountTotal 预计金额合计
 */
public record DashboardWishAggregate(
        long count,
        BigDecimal amountTotal) {
}
