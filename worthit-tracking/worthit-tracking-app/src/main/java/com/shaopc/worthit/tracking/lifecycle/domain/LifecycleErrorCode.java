package com.shaopc.worthit.tracking.lifecycle.domain;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * 生命周期领域稳定错误码。
 */
public enum LifecycleErrorCode implements ErrorCode {

    /** 处置日期、金额或事实字段违反冻结约束。 */
    VAL_INVALID_ARGUMENT(
            "VAL_INVALID_ARGUMENT",
            "物品处置参数不合法");

    private final String code;
    private final String defaultMessage;

    LifecycleErrorCode(
            String code,
            String defaultMessage) {
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
