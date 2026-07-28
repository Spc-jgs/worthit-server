package com.shaopc.worthit.reminder.app.security;

import com.shaopc.worthit.common.security.context.UserContext;

/**
 * 向 Reminder 公网用例提供可信当前用户。
 */
@FunctionalInterface
public interface CurrentUserProvider {

    /**
     * 获取已认证用户。
     *
     * @return 当前用户上下文
     */
    UserContext currentUser();
}
