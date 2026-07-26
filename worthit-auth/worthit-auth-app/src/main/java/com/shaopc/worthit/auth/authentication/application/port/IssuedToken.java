package com.shaopc.worthit.auth.authentication.application.port;

/**
 * 已签发的用户访问令牌。
 *
 * @param value            Token 值
 * @param expiresInSeconds 剩余有效期，单位秒
 */
public record IssuedToken(String value, long expiresInSeconds) {

    /**
     * 校验令牌响应。
     */
    public IssuedToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Token不能为空");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("Token有效期必须为正数");
        }
    }
}
