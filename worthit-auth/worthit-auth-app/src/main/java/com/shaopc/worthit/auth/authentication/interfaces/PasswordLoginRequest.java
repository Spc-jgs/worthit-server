package com.shaopc.worthit.auth.authentication.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账号密码登录请求。
 */
@Schema(description = "账号密码登录请求")
public record PasswordLoginRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{3,64}")
        @Schema(description = "用户名", example = "local.user")
        String username,
        @NotBlank
        @Size(min = 8, max = 128)
        @Schema(description = "密码", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password) {
}
