package com.shaopc.worthit.tracking.item.domain;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

import java.util.Optional;

/**
 * 物品聚合持久化边界。
 */
public interface ItemRepository {

    /**
     * 保存新物品。
     *
     * @param item 待保存事实
     * @return 带数据库标识的物品
     */
    Item create(Item item);

    /**
     * 查询用户可见的有效物品详情。
     */
    Optional<ItemWithCategory> findByIdAndUserId(
            long itemId, long userId);

    /**
     * 分页查询用户有效物品。
     */
    PageResult<ItemWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId);
}
