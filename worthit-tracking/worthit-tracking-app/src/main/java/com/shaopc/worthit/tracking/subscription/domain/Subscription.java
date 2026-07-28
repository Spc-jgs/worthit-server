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
        String status,
        String remark,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    /** 有效订阅状态。 */
    public static final String ACTIVE = "ACTIVE";
    /** 暂停订阅状态。 */
    public static final String PAUSED = "PAUSED";
    /** 结束订阅状态。 */
    public static final String ENDED = "ENDED";
}
