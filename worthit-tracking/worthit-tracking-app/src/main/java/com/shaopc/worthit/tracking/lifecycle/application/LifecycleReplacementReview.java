package com.shaopc.worthit.tracking.lifecycle.application;

/**
 * 生命周期复盘中的替换分支。
 */
public record LifecycleReplacementReview(
        LifecycleItemBrief oldItem,
        LifecycleItemBrief newItem) {
}
