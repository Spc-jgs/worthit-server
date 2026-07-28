package com.shaopc.worthit.tracking.subscription.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * 订阅稳定业务错误码。
 */
public enum SubscriptionErrorCode implements ErrorCode {

    VAL_STATE_CONFLICT(
            "VAL_STATE_CONFLICT",
            "订阅状态不允许该操作"),
    IDEM_CONFLICT(
            "IDEM_CONFLICT",
            "同一幂等键对应了不同请求");

    private final String code;
    private final String defaultMessage;

    SubscriptionErrorCode(
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
