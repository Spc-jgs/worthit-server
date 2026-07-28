package com.shaopc.worthit.tracking.subscription.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Subscription 与分类名称查询投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionViewDO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal amount;
    private String currency;
    private String billingCycleType;
    private Integer billingCycleValue;
    private BigDecimal cnyReferenceAmount;
    private LocalDate nextRenewalDate;
    private String autoRenew;
    private Boolean renewalReminderEnabled;
    private String status;
    private String remark;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
