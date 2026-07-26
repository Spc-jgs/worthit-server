package com.shaopc.worthit.auth.authentication.infrastructure.session;

import cn.dev33.satoken.stp.StpUtil;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import org.springframework.stereotype.Component;

/**
 * 使用 Sa-Token JWT Simple 和 Redis 实现用户登录会话。
 */
@Component
public class SaTokenUserSession implements UserSession {

    @Override
    public IssuedToken login(long userId) {
        StpUtil.login(userId);
        return new IssuedToken(
                StpUtil.getTokenValue(),
                StpUtil.getTokenTimeout());
    }

    @Override
    public long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
