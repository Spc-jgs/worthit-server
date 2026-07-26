package com.shaopc.worthit.auth.authentication.interfaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功返回的用户信息。
 */
@Schema(description = "登录用户信息")
public record LoginUserResponse(
        @Schema(description = "内部用户标识")
        String id,
        @Schema(description = "用户昵称", nullable = true)
        String nickname,
        @Schema(description = "用户头像地址", nullable = true)
        String avatarUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "本次是否创建新用户", nullable = true)
        Boolean isNewUser) {
}
