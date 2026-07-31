package com.shaopc.worthit.tracking.lifecycle.application;

import java.time.LocalDate;

/**
 * 生命周期复盘中的处置分支。
 */
public record LifecycleDisposalReview(
        LifecycleItemBrief item,
        String type,
        LocalDate date,
        String saleAmount,
        String netCost) {
}
