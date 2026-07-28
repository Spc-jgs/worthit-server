package com.shaopc.worthit.tracking.subscription.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅列表项应用读模型。
 */
public record SubscriptionSummary(
        long id,
        String name,
        String categoryName,
        String amount,
        String currency,
        String originalMonthlyCostDisplay,
        String cnyMonthlyCostDisplay,
        String status,
        LocalDate nextRenewalDate,
        long version,
        LocalDateTime createTime) {
}
