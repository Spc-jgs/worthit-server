package com.shaopc.worthit.tracking.recovery.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * 完整恢复稳定业务错误码。
 */
public enum RecoveryErrorCode implements ErrorCode {

    /** 资源删除状态或版本与请求冲突。 */
    VAL_STATE_CONFLICT(
            "VAL_STATE_CONFLICT",
            "资源状态已变化，请刷新后重试");

    private final String code;
    private final String defaultMessage;

    RecoveryErrorCode(String code, String defaultMessage) {
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
