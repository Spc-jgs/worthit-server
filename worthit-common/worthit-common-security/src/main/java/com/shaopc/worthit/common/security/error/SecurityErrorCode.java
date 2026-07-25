package com.shaopc.worthit.common.security.error;

import com.shaopc.worthit.common.core.error.ErrorCode;

/**
 * 定义认证鉴权失败时对外稳定的错误码。
 */
public enum SecurityErrorCode implements ErrorCode {

    /**
     * 请求未携带有效登录态。
     */
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未登录或登录已失效"),

    /**
     * 当前主体无权访问目标资源。
     */
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "没有权限访问该资源");

    private final String code;
    private final String defaultMessage;

    SecurityErrorCode(String code, String defaultMessage) {
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
