package com.shaopc.worthit.tracking.subscription.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅聚合事实。
 */
public record Subscription(
        long id,
        long userId,
        long categoryId,
        String name,
        BigDecimal amount,
        String currency,
        BillingCycleType billingCycleType,
        Integer billingCycleValue,
        BigDecimal cnyReferenceAmount,
        LocalDate nextRenewalDate,
        AutoRenew autoRenew,
        boolean renewalReminderEnabled,
        SubscriptionStatus status,
        String remark,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
