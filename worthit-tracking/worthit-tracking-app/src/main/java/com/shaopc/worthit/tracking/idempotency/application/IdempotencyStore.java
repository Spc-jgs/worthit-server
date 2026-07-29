package com.shaopc.worthit.tracking.idempotency.application;

/**
 * Tracking 写接口的持久化幂等边界。
 */
public interface IdempotencyStore {

    /**
     * 占用或重放幂等键。
     */
    <T> IdempotencyClaim<T> claim(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType);

    /**
     * 保存首次成功响应。
     */
    <T> void complete(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            T response);
}
