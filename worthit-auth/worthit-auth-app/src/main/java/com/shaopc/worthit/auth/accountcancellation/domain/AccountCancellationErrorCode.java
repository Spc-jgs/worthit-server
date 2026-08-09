package com.shaopc.worthit.auth.accountcancellation.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/** Auth 账号注销稳定业务错误码。 */
public enum AccountCancellationErrorCode implements ErrorCode {

    VAL_STATE_CONFLICT("VAL_STATE_CONFLICT", "账号注销状态已变化"),
    IDEM_CONFLICT("IDEM_CONFLICT", "幂等键已用于不同请求"),
    IDEM_IN_PROGRESS("IDEM_IN_PROGRESS", "相同请求正在处理中");

    private final String code;
    private final String defaultMessage;

    AccountCancellationErrorCode(String code, String defaultMessage) {
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
