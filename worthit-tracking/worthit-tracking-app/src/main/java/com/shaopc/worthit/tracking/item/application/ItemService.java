package com.shaopc.worthit.tracking.item.application;

import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * 物品公开应用用例。
 */
public interface ItemService {

    /** 幂等新建物品。 */
    ItemDetail create(
            String idempotencyKey,
            CreateItemCommand command);

    /** 查询当前用户物品详情。 */
    ItemDetail detail(long itemId);

    /** 分页查询当前用户物品。 */
    PageResult<ItemSummary> list(
            int page,
            int size,
            String keyword,
            Long categoryId);

    /** 按版本幂等更新物品。 */
    ItemDetail update(
            long itemId,
            String idempotencyKey,
            UpdateItemCommand command);

    /** 逻辑删除物品并签发短时恢复令牌。 */
    DeleteItemResult delete(
            long itemId,
            long version,
            String idempotencyKey);

    /** 在短时窗口内幂等恢复物品。 */
    ItemDetail restore(
            long itemId,
            long deletedVersion,
            String restoreToken);
}
