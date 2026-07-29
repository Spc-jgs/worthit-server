package com.shaopc.worthit.tracking.idempotency.application;

import java.time.LocalDateTime;

/**
 * Tracking 短时恢复令牌持久化边界。
 */
public interface RestoreTokenStore {

    /**
     * 为一次成功删除创建恢复令牌。
     */
    String issue(
            long userId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            LocalDateTime deadline);

    /**
     * 锁定并校验恢复令牌。
     */
    <T> RestoreTokenClaim<T> claim(
            long userId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            String restoreToken,
            LocalDateTime now,
            Class<T> responseType);

    /**
     * 保存首次恢复结果，供重复请求幂等重放。
     */
    <T> void complete(
            long userId,
            TrackingOperation operation,
            long resourceId,
            long deletedVersion,
            String restoreToken,
            T response);
}
