package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import java.time.LocalDate;

/**
 * 生命周期复盘处置分支响应。
 */
public record LifecycleDisposalReviewResponse(
        LifecycleItemBriefResponse item,
        String type,
        LocalDate date,
        String saleAmount,
        String netCost) {
}
