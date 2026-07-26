package com.shaopc.worthit.tracking.category.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.category.application.CurrentUserProvider;
import org.springframework.stereotype.Component;

/**
 * 从已校验的 Sa-Token 登录态读取当前用户。
 */
@Component
public class SaTokenCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UserContext currentUser() {
        return new UserContext(StpUtil.getLoginIdAsLong());
    }
}
