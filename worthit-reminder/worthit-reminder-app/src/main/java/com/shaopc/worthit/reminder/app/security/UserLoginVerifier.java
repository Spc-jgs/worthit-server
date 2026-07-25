package com.shaopc.worthit.reminder.app.security;

/**
 * 校验当前请求是否具有有效用户登录态。
 */
@FunctionalInterface
public interface UserLoginVerifier {

    /**
     * 校验当前用户登录态，无效时抛出认证异常。
     */
    void verify();
}
