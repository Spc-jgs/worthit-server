package com.shaopc.worthit.common.webmvc.security;

/**
 * 决定公网请求是否需要用户登录态。
 */
@FunctionalInterface
public interface PublicRequestAuthorizationPolicy {

    /**
     * 判断指定公网请求路径是否要求用户登录。
     *
     * @param requestPath Servlet 请求路径
     * @return 需要登录时返回 {@code true}
     */
    boolean requiresLogin(String requestPath);
}
