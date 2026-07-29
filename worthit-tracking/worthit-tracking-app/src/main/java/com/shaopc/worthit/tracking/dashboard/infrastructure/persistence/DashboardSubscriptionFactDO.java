package com.shaopc.worthit.tracking.dashboard.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dashboard 订阅成本事实查询投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class DashboardSubscriptionFactDO {

    private BigDecimal amount;
    private String currency;
    private String billingCycleType;
    private Integer billingCycleValue;
    private BigDecimal cnyReferenceAmount;
}
