package com.shaopc.worthit.tracking.dashboard.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页 Dashboard 公网响应。
 *
 * @param itemPlanDailyTotal 物品计划日均合计金额字符串
 * @param itemPlanDailyTotalDisplay 物品计划日均展示文案
 * @param itemResidualUnsetCount 未填写预计残值的物品数
 * @param subscriptionMonthlyCnyTotal 订阅人民币参考月成本合计
 * @param subscriptionMonthlyCnyTotalDisplay 订阅月成本展示文案
 * @param subscriptionMonthlyCnyApproximate 合计是否包含外币人民币参考
 * @param subscriptionUnconvertedForeignCount 未折算外币订阅数
 * @param wishConsideringCount 考虑中的想买数量
 * @param wishConsideringAmountTotal 考虑中的想买金额合计
 * @param wishConsideringAmountTotalDisplay 想买金额展示文案
 */
@Schema(description = "首页实时成本汇总")
public record DashboardResponse(
        @Schema(
                description = "当前持有物品计划日均合计",
                example = "12.34")
        String itemPlanDailyTotal,
        @Schema(
                description = "物品计划日均合计展示文案",
                example = "¥12.34/天")
        String itemPlanDailyTotalDisplay,
        @Schema(
                description = "未填写预计残值的持有物品数量",
                example = "2")
        long itemResidualUnsetCount,
        @Schema(
                description = "有效订阅人民币参考月成本合计",
                example = "150.00")
        String subscriptionMonthlyCnyTotal,
        @Schema(
                description = "订阅人民币参考月成本展示文案",
                example = "约 ¥150.00/月")
        String subscriptionMonthlyCnyTotalDisplay,
        @Schema(
                description = "月成本合计是否包含外币人民币参考",
                example = "true")
        boolean subscriptionMonthlyCnyApproximate,
        @Schema(
                description = "未填写人民币参考的有效外币订阅数量",
                example = "1")
        long subscriptionUnconvertedForeignCount,
        @Schema(
                description = "当前考虑中的想买数量",
                example = "3")
        long wishConsideringCount,
        @Schema(
                description = "当前考虑中的想买预计金额合计",
                example = "5000.00")
        String wishConsideringAmountTotal,
        @Schema(
                description = "想买预计金额合计展示文案",
                example = "¥5000.00")
        String wishConsideringAmountTotalDisplay) {
}
