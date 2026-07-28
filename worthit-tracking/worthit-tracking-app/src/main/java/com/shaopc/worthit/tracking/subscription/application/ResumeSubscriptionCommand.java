package com.shaopc.worthit.tracking.subscription.application;

import java.time.LocalDate;

/**
 * 恢复订阅命令。
 */
public record ResumeSubscriptionCommand(
        long version,
        LocalDate nextRenewalDate,
        Boolean renewalReminderEnabled) {
}
