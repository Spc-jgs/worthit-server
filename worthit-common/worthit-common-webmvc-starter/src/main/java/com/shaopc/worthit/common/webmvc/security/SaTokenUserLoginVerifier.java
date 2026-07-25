package com.shaopc.worthit.common.webmvc.security;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 使用 Sa-Token 校验当前用户登录态。
 */
public final class SaTokenUserLoginVerifier implements UserLoginVerifier {

    @Override
    public void verify() {
        StpUtil.checkLogin();
    }
}
