package com.shaopc.worthit.tracking.idempotency.application;

/**
 * 短时恢复令牌校验结果。
 *
 * @param status 校验状态
 * @param replay 已成功恢复时的历史结果
 */
public record RestoreTokenClaim<T>(
        Status status,
        T replay) {

    /**
     * 恢复令牌状态。
     */
    public enum Status {
        /** 令牌有效，可以执行首次恢复。 */
        AVAILABLE,
        /** 恢复已经完成，应返回历史结果。 */
        REPLAY,
        /** 令牌与资源或版本不匹配。 */
        CONFLICT,
        /** 令牌不存在或已经超过恢复窗口。 */
        EXPIRED
    }
}
