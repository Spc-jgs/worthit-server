package com.shaopc.worthit.tracking.dashboard.application;

import java.math.BigDecimal;

/**
 * Dashboard 计算物品计划日均所需的最小事实。
 *
 * @param purchasePrice 购买价格
 * @param expectedYears 预计使用年限
 * @param residualValue 预计残值；空表示未计残值
 */
public record DashboardItemFact(
        BigDecimal purchasePrice,
        BigDecimal expectedYears,
        BigDecimal residualValue) {
}
