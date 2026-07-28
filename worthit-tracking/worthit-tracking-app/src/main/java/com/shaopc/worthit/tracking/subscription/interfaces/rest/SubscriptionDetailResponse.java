package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅详情公网响应。
 */
public record SubscriptionDetailResponse(
        String id,
        String name,
        String categoryId,
        String categoryName,
        String amount,
        String currency,
        String billingCycleType,
        Integer billingCycleValue,
        String cnyReferenceAmount,
        LocalDate nextRenewalDate,
        String autoRenew,
        boolean renewalReminderEnabled,
        String status,
        String remark,
        String originalMonthlyCost,
        String originalMonthlyCostDisplay,
        String cnyMonthlyCost,
        String cnyMonthlyCostDisplay,
        boolean cnyApproximate,
        boolean includeInCnyTotal,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
