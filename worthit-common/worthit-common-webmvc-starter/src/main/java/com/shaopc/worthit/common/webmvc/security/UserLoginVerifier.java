package com.shaopc.worthit.common.webmvc.security;

/**
 * 校验当前 Servlet 请求的用户登录态。
 */
@FunctionalInterface
public interface UserLoginVerifier {

    /**
     * 校验当前用户已登录。
     */
    void verify();
}
