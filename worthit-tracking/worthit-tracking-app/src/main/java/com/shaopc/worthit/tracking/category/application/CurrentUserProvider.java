package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.common.security.context.UserContext;

/**
 * 向分类用例提供可信的当前用户上下文。
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
