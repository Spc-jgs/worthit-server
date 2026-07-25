package com.shaopc.worthit.tracking.app.security;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * 使用 Sa-Token 校验当前用户登录态。
 */
@Component
public final class SaTokenUserLoginVerifier implements UserLoginVerifier {

    @Override
    public void verify() {
        StpUtil.checkLogin();
    }
}
