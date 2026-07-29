package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;

/**
 * 微信身份首次用户建档应用用例。
 */
public interface WechatUserRegistrationService {

    /**
     * 查找已有用户，或原子创建用户与外部身份。
     *
     * @param identity 已验证微信身份
     * @return 用户注册结果
     */
    UserRegistration register(WechatIdentity identity);
}
