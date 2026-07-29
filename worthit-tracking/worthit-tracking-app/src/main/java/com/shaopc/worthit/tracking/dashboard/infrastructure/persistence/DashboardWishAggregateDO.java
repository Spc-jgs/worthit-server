package com.shaopc.worthit.tracking.dashboard.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dashboard 想买数量和金额数据库汇总投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class DashboardWishAggregateDO {

    private Long count;
    private BigDecimal amountTotal;
}
