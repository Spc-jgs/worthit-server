package com.shaopc.worthit.tracking.idempotency.application;

import java.time.LocalDateTime;

/**
 * 生命周期写命令使用的持久化幂等执行端口。
 */
public interface IdempotencyExecutionStore {

    /**
     * 首次占位、重放、冲突判断或过期租约接管。
     */
    <T> IdempotencyExecutionClaim<T> claim(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType);

    /**
     * 在业务事务内完成成功结果。
     */
    <T> void completeSuccess(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            T response);

    /**
     * 在业务回滚后的独立事务内固化终结性业务失败。
     */
    void completeFailure(
            long userId,
            TrackingOperation operation,
            String idempotencyKey,
            String requestHash,
            LocalDateTime leaseExpiresAt,
            String errorCode,
            String errorMessage);
}
