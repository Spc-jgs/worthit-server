package com.shaopc.worthit.auth.authentication.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 微信登录请求。
 *
 * @param code 小程序 {@code wx.login} 一次性凭证
 */
@Schema(description = "微信登录请求")
public record WechatLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空")
        @Schema(description = "wx.login返回的一次性凭证")
        String code) {
}
