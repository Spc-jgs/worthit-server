package com.shaopc.worthit.tracking.lifecycle.application;

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
}
