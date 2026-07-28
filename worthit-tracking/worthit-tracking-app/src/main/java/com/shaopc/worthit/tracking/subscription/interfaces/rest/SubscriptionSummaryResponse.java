package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订阅列表项公网响应。
 */
public record SubscriptionSummaryResponse(
        String id,
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
