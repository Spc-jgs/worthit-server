package com.shaopc.worthit.auth.authentication.domain;

import java.util.Locale;

/**
 * 统一账号名的持久化和查询语义。
 */
public final class UsernameNormalizer {

    private UsernameNormalizer() {
    }

    /**
     * 去除首尾空白并转为小写。
     */
    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
