package com.shaopc.worthit.auth.authentication.application.port;

import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;

import java.util.Optional;

/**
 * 认证用例访问用户与外部身份的持久化端口。
 */
public interface AuthUserRepository {

    /**
     * 按微信身份查找内部用户。
     *
     * @param appId           小程序 AppId
     * @param externalSubject 微信 openid
     * @return 对应内部用户
     */
    Optional<AuthUser> findByWechatIdentity(
            String appId, String externalSubject);

    /**
     * 按内部标识查找用户。
     *
     * @param userId 内部用户标识
     * @return 用户
     */
    Optional<AuthUser> findById(long userId);

    /**
     * 原子创建内部用户及其微信身份。
     *
     * @param identity 微信身份
     * @return 新建用户
     */
    AuthUser createWechatUser(WechatIdentity identity);
}
