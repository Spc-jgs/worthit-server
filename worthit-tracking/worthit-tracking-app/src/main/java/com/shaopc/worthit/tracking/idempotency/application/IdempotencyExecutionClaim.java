package com.shaopc.worthit.tracking.idempotency.application;

import java.time.LocalDateTime;

/**
 * 带租约 fencing 信息的幂等执行占位结果。
 *
 * @param status 占位结果
 * @param replay 成功重放结果
 * @param errorCode 失败重放错误码
 * @param errorMessage 失败重放安全消息
 * @param leaseExpiresAt 新执行者持有的租约截止时间
 * @param <T> 公网响应类型
 */
public record IdempotencyExecutionClaim<T>(
        Status status,
        T replay,
        String errorCode,
        String errorMessage,
        LocalDateTime leaseExpiresAt) {

    /**
     * 幂等执行占位状态。
     */
    public enum Status {
        NEW,
        REPLAY_SUCCESS,
        REPLAY_FAILURE,
        IN_PROGRESS,
        CONFLICT
    }
}
