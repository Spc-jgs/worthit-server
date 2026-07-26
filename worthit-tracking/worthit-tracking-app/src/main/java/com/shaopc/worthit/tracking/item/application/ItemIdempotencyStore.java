package com.shaopc.worthit.tracking.item.application;

/**
 * Item 新建接口的持久化幂等边界。
 */
public interface ItemIdempotencyStore {

    /**
     * 占用或重放幂等键。
     */
    ItemIdempotencyClaim claim(
            long userId,
            String idempotencyKey,
            String requestHash);

    /**
     * 保存首次成功响应。
     */
    void complete(
            long userId,
            String idempotencyKey,
            String requestHash,
            ItemDetail response);
}
