package com.shaopc.worthit.auth.authentication.application;

import java.util.Objects;

/**
 * 账号密码登录命令。
 *
 * @param username 用户名
 * @param password 原始密码，仅在当前请求内使用
 */
public record PasswordLoginCommand(
        String username,
        String password) {

    public PasswordLoginCommand {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }
}
