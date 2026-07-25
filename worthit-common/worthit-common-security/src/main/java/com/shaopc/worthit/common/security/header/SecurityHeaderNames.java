package com.shaopc.worthit.common.security.header;

import cn.dev33.satoken.same.SaSameUtil;

/**
 * 定义认证与内部调用链使用的标准请求头名称。
 */
public final class SecurityHeaderNames {

    /**
     * HTTP 标准认证请求头。
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * Sa-Token 内部服务认证请求头。
     */
    public static final String SAME_TOKEN = SaSameUtil.SAME_TOKEN;

    /**
     * 内部调用方服务名称请求头。
     */
    public static final String CALLER_SERVICE = "X-Caller-Service";

    /**
     * 当前用户标识请求头。
     */
    public static final String USER_ID = "X-User-Id";

    /**
     * 当前登录会话标识请求头。
     */
    public static final String SESSION_ID = "X-Session-Id";

    /**
     * 全链路追踪标识请求头。
     */
    public static final String TRACE_ID = "X-Trace-Id";

    private SecurityHeaderNames() {
    }
}
