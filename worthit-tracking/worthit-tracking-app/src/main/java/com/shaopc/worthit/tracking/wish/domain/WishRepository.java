package com.shaopc.worthit.tracking.wish.domain;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 想买聚合持久化边界。
 */
public interface WishRepository {

    Wish create(Wish wish);

    Optional<WishWithCategory> findByIdAndUserId(
            long wishId, long userId);

    Optional<WishWithCategory> findForUpdate(
            long wishId, long userId);

    Optional<WishDeletionState> findDeletionState(
            long wishId, long userId);

    PageResult<WishWithCategory> findPage(
            long userId,
            PageQuery pageQuery,
            String keyword,
            Long categoryId);

    boolean update(Wish wish, long expectedVersion);

    boolean purchase(
            long wishId,
            long userId,
            long expectedVersion,
            long itemId,
            String conversionKey,
            LocalDateTime now);

    boolean changeStatus(
            long wishId,
            long userId,
            long expectedVersion,
            WishStatus expectedStatus,
            WishStatus targetStatus,
            String abandonReason,
            LocalDateTime abandonAt,
            LocalDateTime now);

    boolean delete(
            long wishId,
            long userId,
            long expectedVersion,
            LocalDateTime now);

    boolean restore(
            long wishId,
            long userId,
            long deletedVersion,
            LocalDateTime now);

    /** 按删除后版本恢复到完整恢复协议选定的分类。 */
    boolean restoreToCategory(
            long wishId,
            long userId,
            long deletedVersion,
            long categoryId,
            LocalDateTime now);
}
