package com.shaopc.worthit.tracking.dashboard.application;

/**
 * Dashboard 应用层汇总结果。
 *
 * @param itemPlanDailyTotal 物品计划日均合计
 * @param itemPlanDailyTotalDisplay 物品计划日均展示
 * @param itemResidualUnsetCount 未计残值物品数
 * @param subscriptionMonthlyCnyTotal 订阅人民币参考月成本合计
 * @param subscriptionMonthlyCnyTotalDisplay 订阅月成本展示
 * @param subscriptionMonthlyCnyApproximate 是否包含外币人民币参考
 * @param subscriptionUnconvertedForeignCount 外币未折算数
 * @param wishConsideringCount 考虑中想买数
 * @param wishConsideringAmountTotal 考虑中想买金额合计
 * @param wishConsideringAmountTotalDisplay 想买金额展示
 */
public record DashboardResult(
        String itemPlanDailyTotal,
        String itemPlanDailyTotalDisplay,
        long itemResidualUnsetCount,
        String subscriptionMonthlyCnyTotal,
        String subscriptionMonthlyCnyTotalDisplay,
        boolean subscriptionMonthlyCnyApproximate,
        long subscriptionUnconvertedForeignCount,
        long wishConsideringCount,
        String wishConsideringAmountTotal,
        String wishConsideringAmountTotalDisplay) {
}
