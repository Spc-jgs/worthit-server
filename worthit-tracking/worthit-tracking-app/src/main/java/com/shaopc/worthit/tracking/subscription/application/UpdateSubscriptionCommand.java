package com.shaopc.worthit.tracking.subscription.application;

import com.shaopc.worthit.tracking.subscription.domain.AutoRenew;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 更新订阅命令。
 */
public record UpdateSubscriptionCommand(
        long version,
        String name,
        Long categoryId,
        BigDecimal amount,
        String currency,
        BillingCycleType billingCycleType,
        Integer billingCycleValue,
        BigDecimal cnyReferenceAmount,
        LocalDate nextRenewalDate,
        AutoRenew autoRenew,
        Boolean renewalReminderEnabled,
        String remark) {
}
