package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.domain.AuthUser;

/**
 * 认证登录态公开应用用例。
 */
public interface AuthenticationService {

    /**
     * 使用微信一次性 code 登录。
     *
     * @param command 微信登录命令
     * @return 登录结果
     */
    AuthenticationResult login(WechatLoginCommand command);

    /**
     * 查询当前登录用户。
     *
     * @return 当前用户
     */
    AuthUser currentUser();

    /**
     * 注销当前登录态。
     */
    void logout();
}
