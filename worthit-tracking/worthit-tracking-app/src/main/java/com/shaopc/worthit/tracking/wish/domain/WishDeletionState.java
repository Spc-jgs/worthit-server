package com.shaopc.worthit.tracking.wish.domain;

import java.time.LocalDateTime;

/**
 * 包含逻辑删除信息的想买状态。
 */
public record WishDeletionState(
        Wish wish,
        boolean deleted,
        LocalDateTime deleteTime) {
}
