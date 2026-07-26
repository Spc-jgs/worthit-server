package com.shaopc.worthit.auth.authentication.application.port;

import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.PasswordCredential;

import java.util.Optional;

/**
 * 账号密码凭证持久化端口。
 */
public interface PasswordCredentialRepository {

    /**
     * 按规范化账号名查询凭证。
     */
    Optional<PasswordCredential> findByUsername(String username);

    /**
     * 判断规范化账号名是否已存在。
     */
    boolean existsByUsername(String username);

    /**
     * 创建内部用户及其账号密码凭证。
     */
    AuthUser createAccount(
            String username,
            String passwordHash,
            String nickname);
}
