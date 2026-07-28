package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * 恢复订阅公网请求。
 */
public record ResumeSubscriptionRequest(
        @Positive(message = "版本必须大于0")
        long version,
        LocalDate nextRenewalDate,
        Boolean renewalReminderEnabled) {
}
