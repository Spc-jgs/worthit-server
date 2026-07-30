package com.shaopc.worthit.tracking.idempotency.application;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * Tracking 公网写命令共用的幂等错误码。
 */
public enum IdempotencyErrorCode implements ErrorCode {

    /** 同一幂等键对应了不同请求摘要。 */
    IDEM_CONFLICT(
            "IDEM_CONFLICT",
            "同一幂等键对应了不同请求"),

    /** 同一请求仍在有效租约内执行。 */
    IDEM_IN_PROGRESS(
            "IDEM_IN_PROGRESS",
            "请求正在处理中，请稍后使用同一幂等键重试");

    private final String code;
    private final String defaultMessage;

    IdempotencyErrorCode(
            String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
