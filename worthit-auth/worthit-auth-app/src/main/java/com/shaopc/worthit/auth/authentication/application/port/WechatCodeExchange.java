package com.shaopc.worthit.auth.authentication.application.port;

import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;

/**
 * 使用一次性登录 code 换取微信身份的外部端口。
 */
@FunctionalInterface
public interface WechatCodeExchange {

    /**
     * 交换微信登录 code。
     *
     * @param code 小程序 {@code wx.login} 一次性凭证
     * @return 已验证的微信身份
     */
    WechatIdentity exchange(String code);
}
