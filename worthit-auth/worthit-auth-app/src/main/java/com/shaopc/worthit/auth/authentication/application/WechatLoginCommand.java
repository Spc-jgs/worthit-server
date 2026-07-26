package com.shaopc.worthit.auth.authentication.application;

/**
 * 微信登录应用命令。
 *
 * @param code 小程序一次性登录凭证
 */
public record WechatLoginCommand(String code) {

    /**
     * 校验登录凭证。
     */
    public WechatLoginCommand {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("微信登录凭证不能为空");
        }
    }
}
