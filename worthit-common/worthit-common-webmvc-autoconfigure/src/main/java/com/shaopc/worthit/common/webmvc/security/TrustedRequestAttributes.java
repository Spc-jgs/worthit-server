package com.shaopc.worthit.common.webmvc.security;

/**
 * 可信 Servlet 请求在过滤链内部使用的属性名。
 */
public final class TrustedRequestAttributes {

    /**
     * 当前请求已通过 Gateway Same-Token 校验。
     */
    public static final String TRUSTED_SOURCE =
            "com.shaopc.worthit.trusted-source";

    private TrustedRequestAttributes() {
    }
}
