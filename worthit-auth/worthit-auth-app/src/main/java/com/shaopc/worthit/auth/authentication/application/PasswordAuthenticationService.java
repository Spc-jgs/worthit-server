package com.shaopc.worthit.auth.authentication.application;

/**
 * 账号密码认证公开应用用例。
 */
public interface PasswordAuthenticationService {

    /**
     * 校验账号密码并签发登录态。
     *
     * @param command 密码登录命令
     * @return 登录结果
     */
    AuthenticationResult login(PasswordLoginCommand command);
}
