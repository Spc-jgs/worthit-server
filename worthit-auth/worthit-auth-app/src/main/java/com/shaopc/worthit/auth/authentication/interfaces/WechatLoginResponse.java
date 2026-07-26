package com.shaopc.worthit.auth.authentication.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 微信登录响应。
 *
 * @param token     访问令牌
 * @param tokenType Authorization 头使用的令牌类型
 * @param expiresIn 令牌有效期，单位秒
 * @param user      登录用户
 */
@Schema(description = "微信登录结果")
public record WechatLoginResponse(
        @Schema(description = "访问令牌")
        String token,
        @Schema(description = "令牌类型", example = "Bearer")
        String tokenType,
        @Schema(description = "剩余有效期，单位秒")
        long expiresIn,
        @Schema(description = "登录用户")
        WechatLoginUserResponse user) {
}
