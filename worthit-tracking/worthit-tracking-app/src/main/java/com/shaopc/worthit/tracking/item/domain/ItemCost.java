package com.shaopc.worthit.tracking.item.domain;

import java.math.BigDecimal;

/**
 * 物品成本派生结果。
 *
 * @param expectedUseDays 预计使用天数
 * @param exactPlanDailyCost 未舍入计划日均
 * @param planDailyCost 两位展示计划日均
 * @param planDailyCostDisplay 计划日均展示文案
 * @param planDailyCostTiny 是否为正数但不足一分
 * @param residualUnset 是否未填写残值
 * @param holdingDays 持有天数；未填写购买日期时为空
 * @param exactHoldingDailyCost 未舍入持有日均
 * @param holdingDailyCost 两位展示持有日均
 * @param holdingDailyCostDisplay 持有日均展示文案
 */
public record ItemCost(
        int expectedUseDays,
        BigDecimal exactPlanDailyCost,
        BigDecimal planDailyCost,
        String planDailyCostDisplay,
        boolean planDailyCostTiny,
        boolean residualUnset,
        Integer holdingDays,
        BigDecimal exactHoldingDailyCost,
        BigDecimal holdingDailyCost,
        String holdingDailyCostDisplay) {
}
