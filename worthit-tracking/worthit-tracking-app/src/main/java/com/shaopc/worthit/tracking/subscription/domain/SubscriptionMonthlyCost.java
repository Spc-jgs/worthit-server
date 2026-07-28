package com.shaopc.worthit.tracking.subscription.domain;

import java.math.BigDecimal;

/**
 * 订阅标准化月成本。
 */
public record SubscriptionMonthlyCost(
        BigDecimal originalMonthlyCost,
        String originalMonthlyCostDisplay,
        BigDecimal cnyMonthlyCost,
        String cnyMonthlyCostDisplay,
        boolean cnyApproximate,
        boolean includeInCnyTotal) {
}
