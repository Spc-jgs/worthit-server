package com.shaopc.worthit.auth.authentication.domain;

/**
 * 微信小程序身份交换结果。
 *
 * @param appId           小程序 AppId
 * @param externalSubject 微信 openid
 * @param unionId         微信 unionid，未返回时为空
 */
public record WechatIdentity(
        String appId,
        String externalSubject,
        String unionId) {

    /**
     * 校验微信身份的稳定业务键。
     */
    public WechatIdentity {
        appId = requireText(appId, "微信小程序AppId");
        externalSubject = requireText(externalSubject, "微信openid");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
