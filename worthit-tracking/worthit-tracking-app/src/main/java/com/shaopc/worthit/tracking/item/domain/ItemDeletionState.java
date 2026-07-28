package com.shaopc.worthit.tracking.item.domain;

import java.time.LocalDateTime;

/**
 * 物品逻辑删除状态。
 *
 * @param item 物品事实
 * @param deleted 是否已逻辑删除
 * @param deleteTime 删除时间
 */
public record ItemDeletionState(
        Item item,
        boolean deleted,
        LocalDateTime deleteTime) {
}
