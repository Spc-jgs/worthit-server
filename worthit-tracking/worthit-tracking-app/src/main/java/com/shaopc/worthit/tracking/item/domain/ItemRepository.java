package com.shaopc.worthit.tracking.item.domain;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

import java.time.LocalDateTime;
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
     * 从想买转换创建物品，并写入数据库唯一来源标识。
     */
    Item createFromWish(Item item, long sourceWishId);

    /**
     * 查询用户可见的有效物品详情。
     */
    Optional<ItemWithCategory> findByIdAndUserId(
            long itemId, long userId);

    /**
     * 查询当前用户由指定想买转换出的有效物品。
     */
    Optional<ItemWithCategory> findBySourceWishId(
            long sourceWishId, long userId);

    /**
     * 查询用户物品的删除状态，包含已逻辑删除数据。
     */
    Optional<ItemDeletionState> findDeletionState(
            long itemId, long userId);

    /**
     * 按版本更新用户的有效物品。
     *
     * @return 条件更新成功时为 true
     */
    boolean update(Item item, long expectedVersion);

    /**
     * 按版本和持有状态原子进入生命周期终态，同时关闭保修提醒。
     *
     * @return 条件更新恰好一行时为 true
     */
    boolean dispose(
            long itemId,
            long userId,
            long expectedVersion,
            ItemLifecycleStatus targetStatus,
            LocalDateTime now);

    /**
     * 按版本逻辑删除用户物品。
     *
     * @return 条件更新成功时为 true
     */
    boolean delete(
            long itemId,
            long userId,
            long expectedVersion,
            LocalDateTime now);

    /**
     * 按删除后版本恢复用户物品。
     *
     * @return 条件更新成功时为 true
     */
    boolean restore(
            long itemId,
            long userId,
            long deletedVersion,
            LocalDateTime now);

    /**
     * 分页查询用户有效物品。
     */
    PageResult<ItemWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId);
}
