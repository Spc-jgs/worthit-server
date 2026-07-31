package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

/**
 * 生命周期复盘替换分支响应。
 */
public record LifecycleReplacementReviewResponse(
        LifecycleItemBriefResponse oldItem,
        LifecycleItemBriefResponse newItem) {
}
