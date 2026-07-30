package com.shaopc.worthit.tracking.lifecycle.domain;

import java.util.Optional;

/**
 * 物品处置事实持久化边界。
 */
public interface ItemDisposalRepository {

    /**
     * 按物品与用户查询处置事实。
     */
    Optional<ItemDisposal> findByItemIdAndUserId(
            long itemId, long userId);
}
