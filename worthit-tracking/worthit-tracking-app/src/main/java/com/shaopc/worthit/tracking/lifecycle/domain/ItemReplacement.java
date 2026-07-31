package com.shaopc.worthit.tracking.lifecycle.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 不可变的物品替换关系事实。
 */
public record ItemReplacement(
        long id,
        long userId,
        long oldItemId,
        long newItemId,
        LocalDateTime createTime) {

    /**
     * 校验替换关系的持久化不变量。
     */
    public ItemReplacement {
        if (id <= 0 || userId <= 0
                || oldItemId <= 0 || newItemId <= 0
                || oldItemId == newItemId) {
            throw new IllegalArgumentException(
                    "替换关系标识必须有效且物品不能相同");
        }
        Objects.requireNonNull(
                createTime, "替换关系创建时间不能为空");
    }
}
