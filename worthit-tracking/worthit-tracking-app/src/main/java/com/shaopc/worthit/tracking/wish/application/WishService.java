package com.shaopc.worthit.tracking.wish.application;

import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * 想买公开应用用例。
 */
public interface WishService {

    /** 幂等新建想买。 */
    WishDetail create(
            String idempotencyKey,
            CreateWishCommand command);

    /** 查询当前用户想买详情。 */
    WishDetail detail(long wishId);

    /** 分页查询当前用户想买。 */
    PageResult<WishSummary> list(
            int page,
            int size,
            String keyword,
            Long categoryId);

    /** 按版本更新考虑中的想买。 */
    WishDetail update(
            long wishId,
            String idempotencyKey,
            UpdateWishCommand command);

    /** 将考虑中的想买转换为物品。 */
    WishPurchaseResult purchase(
            long wishId,
            long version,
            String idempotencyKey);

    /** 放弃考虑中的想买。 */
    WishDetail abandon(
            long wishId,
            long version,
            String reason,
            String idempotencyKey);

    /** 将已放弃想买重新置为考虑中。 */
    WishDetail reconsider(
            long wishId,
            long version,
            String idempotencyKey);

    /** 逻辑删除想买并签发短时恢复令牌。 */
    DeleteWishResult delete(
            long wishId,
            long version,
            String idempotencyKey);

    /** 在短时窗口内幂等恢复想买。 */
    WishDetail restore(
            long wishId,
            long deletedVersion,
            String restoreToken);
}
