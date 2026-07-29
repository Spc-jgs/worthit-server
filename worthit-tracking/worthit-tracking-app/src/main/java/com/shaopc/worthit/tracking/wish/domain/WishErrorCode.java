package com.shaopc.worthit.tracking.wish.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * Wish 稳定业务错误码。
 */
public enum WishErrorCode implements ErrorCode {

    /** 状态、版本或恢复凭据冲突。 */
    VAL_STATE_CONFLICT(
            "VAL_STATE_CONFLICT", "想买状态不允许该操作"),
    /** 同一幂等键对应了不同请求。 */
    IDEM_CONFLICT(
            "IDEM_CONFLICT", "同一幂等键对应了不同请求");

    private final String code;
    private final String defaultMessage;

    WishErrorCode(String code, String defaultMessage) {
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
