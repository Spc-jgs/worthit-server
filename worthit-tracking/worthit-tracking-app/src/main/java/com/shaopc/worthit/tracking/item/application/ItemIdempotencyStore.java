package com.shaopc.worthit.tracking.item.application;

/**
 * Item 写接口的持久化幂等边界。
 */
public interface ItemIdempotencyStore {

    /**
     * 占用或重放幂等键。
     */
    <T> ItemIdempotencyClaim<T> claim(
            long userId,
            String operationCode,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType);

    /**
     * 保存首次成功响应。
     */
    <T> void complete(
            long userId,
            String operationCode,
            String idempotencyKey,
            String requestHash,
            T response);
}
