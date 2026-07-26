package com.shaopc.worthit.auth.app.security;

import com.shaopc.worthit.common.webmvc.security.PublicRequestAuthorizationPolicy;
import org.springframework.stereotype.Component;

/**
 * 认证服务公网请求的用户登录校验策略。
 */
@Component
public final class AuthPublicRequestAuthorizationPolicy
        implements PublicRequestAuthorizationPolicy {

    private static final String WECHAT_LOGIN_PATH =
            "/api/v1/auth/wechat/login";
    private static final String PASSWORD_LOGIN_PATH =
            "/api/v1/auth/password/login";

    @Override
    public boolean requiresLogin(String requestPath) {
        return !WECHAT_LOGIN_PATH.equals(requestPath)
                && !PASSWORD_LOGIN_PATH.equals(requestPath);
    }
}
