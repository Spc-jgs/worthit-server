package com.shaopc.worthit.auth.authentication.application.port;

/**
 * 密码单向哈希端口。
 */
public interface PasswordHasher {

    /**
     * 生成带算法标识的密码哈希。
     */
    String encode(CharSequence rawPassword);

    /**
     * 校验原始密码是否匹配已存哈希。
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
