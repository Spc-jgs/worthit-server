package com.shaopc.worthit.common.web.error;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * 定义跨服务统一 Web 边界使用的稳定错误码。
 */
public enum CommonWebErrorCode implements ErrorCode {

    /**
     * 请求参数不符合接口约束。
     */
    VAL_INVALID_ARGUMENT("VAL_INVALID_ARGUMENT", "参数不合法"),

    /**
     * 目标资源不存在或不可见。
     */
    RES_NOT_FOUND("RES_NOT_FOUND", "资源不存在"),

    /**
     * 服务内部发生未预期错误。
     */
    SYS_ERROR("SYS_ERROR", "系统错误"),

    /**
     * 下游服务或依赖不可用。
     */
    SYS_UPSTREAM("SYS_UPSTREAM", "下游服务暂时不可用");

    private final String code;
    private final String defaultMessage;

    CommonWebErrorCode(String code, String defaultMessage) {
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
