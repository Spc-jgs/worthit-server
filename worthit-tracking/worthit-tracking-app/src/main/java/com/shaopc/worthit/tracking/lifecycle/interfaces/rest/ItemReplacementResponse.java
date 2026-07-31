package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import java.time.LocalDateTime;

/**
 * 替换关系公网响应。
 */
public record ItemReplacementResponse(
        String relationId,
        LifecycleItemBriefResponse oldItem,
        LifecycleItemBriefResponse newItem,
        LocalDateTime createTime) {
}
