package com.shaopc.worthit.auth.authentication.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 微信登录返回的用户信息。
 *
 * @param id        字符串形式的内部用户标识
 * @param nickname  用户昵称
 * @param avatarUrl 用户头像地址
 * @param isNewUser 本次是否创建新用户
 */
@Schema(description = "微信登录用户信息")
public record WechatLoginUserResponse(
        @Schema(description = "内部用户标识")
        String id,
        @Schema(description = "用户昵称", nullable = true)
        String nickname,
        @Schema(description = "用户头像地址", nullable = true)
        String avatarUrl,
        @Schema(description = "本次是否创建新用户")
        boolean isNewUser) {
}
