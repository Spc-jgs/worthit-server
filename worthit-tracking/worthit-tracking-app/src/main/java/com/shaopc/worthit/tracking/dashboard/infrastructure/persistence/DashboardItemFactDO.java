package com.shaopc.worthit.tracking.dashboard.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dashboard 物品成本事实查询投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class DashboardItemFactDO {

    private BigDecimal purchasePrice;
    private BigDecimal expectedYears;
    private BigDecimal residualValue;
}
