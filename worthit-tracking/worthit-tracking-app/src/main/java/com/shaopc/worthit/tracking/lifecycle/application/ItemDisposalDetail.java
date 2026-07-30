package com.shaopc.worthit.tracking.lifecycle.application;

import com.shaopc.worthit.tracking.lifecycle.domain.ItemDisposal;

import java.time.LocalDate;

/**
 * 公网生命周期响应复用的处置事实。
 */
public record ItemDisposalDetail(
        String type,
        LocalDate date,
        String saleAmount,
        String remark,
        String netCost) {

    /**
     * 从领域事实创建只读用例结果。
     */
    public static ItemDisposalDetail from(
            ItemDisposal disposal) {
        return new ItemDisposalDetail(
                disposal.type().code(),
                disposal.disposalDate(),
                plain(disposal.saleAmount()),
                disposal.remark(),
                plain(disposal.netCost()));
    }

    private static String plain(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
