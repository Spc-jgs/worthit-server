package com.shaopc.worthit.tracking.lifecycle.application;

import java.time.LocalDateTime;

/**
 * 物品处置用例统一结果。
 */
public record ItemLifecycleResult(
        long itemId,
        String lifecycleStatus,
        ItemDisposalDetail disposal,
        long version,
        LocalDateTime updateTime) {
}
