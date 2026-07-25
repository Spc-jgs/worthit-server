package com.shaopc.worthit.common.security.context;

/**
 * 表示经可信认证链路解析出的当前用户上下文。
 *
 * @param userId 当前用户标识
 */
public record UserContext(long userId) {

    /**
     * 创建当前用户上下文。
     *
     * @throws IllegalArgumentException 当用户标识不是正数时抛出
     */
    public UserContext {
        if (userId <= 0) {
            throw new IllegalArgumentException("用户标识必须大于0");
        }
    }
}
