package com.shaopc.worthit.auth.authentication.domain;

import java.util.Objects;

/**
 * 账号密码凭证及其关联用户。
 *
 * @param user         内部用户
 * @param passwordHash 不可逆密码哈希
 */
public record PasswordCredential(
        AuthUser user,
        String passwordHash) {

    public PasswordCredential {
        Objects.requireNonNull(user, "user");
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash 不能为空");
        }
    }
}
