package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生命周期复盘显式判别联合响应。
 */
public record LifecycleReviewEntryResponse(
        String id,
        String entryType,
        LocalDate eventDate,
        LocalDateTime createTime,
        LifecycleDisposalReviewResponse disposal,
        LifecycleReplacementReviewResponse replacement) {
}
