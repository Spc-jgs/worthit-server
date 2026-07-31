package com.shaopc.worthit.tracking.lifecycle.application;

import java.time.LocalDateTime;

/**
 * 建立替换关系后的冻结结果。
 */
public record ItemReplacementResult(
        long relationId,
        LifecycleItemBrief oldItem,
        LifecycleItemBrief newItem,
        LocalDateTime createTime) {
}
