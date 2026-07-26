package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;

/**
 * 微信登录用例结果。
 *
 * @param token   已签发访问令牌
 * @param user    内部用户
 * @param newUser 本次是否创建新用户
 */
public record AuthenticationResult(
        IssuedToken token,
        AuthUser user,
        boolean newUser) {
}
