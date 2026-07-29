package com.shaopc.worthit.tracking.dashboard.application;

import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;

import java.math.BigDecimal;

/**
 * Dashboard 计算订阅人民币参考月成本所需的最小事实。
 *
 * @param amount 原币周期金额
 * @param currency 币种代码
 * @param billingCycleType 计费周期类型
 * @param billingCycleValue 多月月数或固定天数
 * @param cnyReferenceAmount 人民币参考周期金额
 */
public record DashboardSubscriptionFact(
        BigDecimal amount,
        String currency,
        BillingCycleType billingCycleType,
        Integer billingCycleValue,
        BigDecimal cnyReferenceAmount) {
}
