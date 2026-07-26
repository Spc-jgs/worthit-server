package com.shaopc.worthit.auth.authentication.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功响应。
 */
@Schema(description = "登录结果")
public record LoginResponse(
        @Schema(description = "访问令牌")
        String token,
        @Schema(description = "令牌类型", example = "Bearer")
        String tokenType,
        @Schema(description = "剩余有效期，单位秒")
        long expiresIn,
        @Schema(description = "登录用户")
        LoginUserResponse user) {
}
