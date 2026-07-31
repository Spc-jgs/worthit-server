package com.shaopc.worthit.tracking.lifecycle.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 生命周期复盘显式判别联合条目。
 */
public record LifecycleReviewEntry(
        long id,
        LifecycleReviewEntryType entryType,
        LocalDate eventDate,
        LocalDateTime createTime,
        LifecycleDisposalReview disposal,
        LifecycleReplacementReview replacement) {

    /**
     * 保证两个分支恰好一个非空。
     */
    public LifecycleReviewEntry {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "复盘事实标识必须为正数");
        }
        Objects.requireNonNull(
                entryType, "复盘条目类型不能为空");
        Objects.requireNonNull(
                eventDate, "复盘事件日期不能为空");
        Objects.requireNonNull(
                createTime, "复盘创建时间不能为空");
        boolean disposalPresent = disposal != null;
        boolean replacementPresent = replacement != null;
        if (disposalPresent == replacementPresent
                || (entryType
                        == LifecycleReviewEntryType.DISPOSAL
                        && !disposalPresent)
                || (entryType
                        == LifecycleReviewEntryType.REPLACEMENT
                        && !replacementPresent)) {
            throw new IllegalArgumentException(
                    "复盘条目分支与判别类型不一致");
        }
    }
}
