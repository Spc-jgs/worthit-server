package com.shaopc.worthit.tracking.lifecycle.application;

import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * 物品生命周期写用例。
 */
public interface ItemLifecycleService {

    /**
     * 把持有中物品退货。
     */
    ItemLifecycleResult returnItem(
            long itemId,
            String idempotencyKey,
            ReturnItemCommand command);

    /**
     * 把持有中物品卖出。
     */
    ItemLifecycleResult sellItem(
            long itemId,
            String idempotencyKey,
            SellItemCommand command);

    /**
     * 把持有中物品报废。
     */
    ItemLifecycleResult scrapItem(
            long itemId,
            String idempotencyKey,
            ScrapItemCommand command);

    /**
     * 建立旧物品到新物品的替换关系。
     */
    ItemReplacementResult replaceItem(
            long oldItemId,
            String idempotencyKey,
            ReplaceItemCommand command);

    /**
     * 分页查询当前用户的生命周期复盘。
     */
    PageResult<LifecycleReviewEntry> review(
            int page, int size);
}
