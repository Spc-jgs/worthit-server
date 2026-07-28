package com.shaopc.worthit.tracking.subscription.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅详情应用读模型。
 */
public record SubscriptionDetail(
        long id,
        String name,
        long categoryId,
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
