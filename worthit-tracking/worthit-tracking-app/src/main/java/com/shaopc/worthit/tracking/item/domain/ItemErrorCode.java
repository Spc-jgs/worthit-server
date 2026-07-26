package com.shaopc.worthit.tracking.item.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * Item 稳定业务错误码。
 */
public enum ItemErrorCode implements ErrorCode {

    /** 同一幂等键对应了不同的请求内容。 */
    IDEM_CONFLICT("IDEM_CONFLICT", "幂等键已用于不同请求");

    private final String code;
    private final String defaultMessage;

    ItemErrorCode(String code, String defaultMessage) {
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
