package com.shaopc.worthit.auth.authentication.interfaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 认证接口用户响应。
 *
 * @param id        字符串形式的内部用户标识
 * @param nickname  用户昵称
 * @param avatarUrl 用户头像地址
 */
@Schema(description = "当前认证用户")
public record AuthUserResponse(
        @Schema(description = "内部用户标识")
        String id,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "用户昵称", nullable = true)
        String nickname,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "用户头像地址", nullable = true)
        String avatarUrl) {
}
