package com.shaopc.worthit.auth.authentication.domain;

/**
 * 认证上下文中的内部用户。
 *
 * @param id        内部稳定用户标识
 * @param nickname  用户昵称
 * @param avatarUrl 用户头像访问地址
 * @param active    账号是否允许登录
 */
public record AuthUser(
        long id,
        String nickname,
        String avatarUrl,
        boolean active) {

    /**
     * 校验内部用户标识。
     */
    public AuthUser {
        if (id <= 0) {
            throw new IllegalArgumentException("用户标识必须为正数");
        }
    }
}
