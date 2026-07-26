package com.shaopc.worthit.auth.authentication.interfaces;

import com.shaopc.worthit.auth.authentication.application.AuthenticationResult;
import com.shaopc.worthit.auth.authentication.application.AuthenticationService;
import com.shaopc.worthit.auth.authentication.application.WechatLoginCommand;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth 公网登录与当前用户接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证", description = "微信登录、登出与当前用户")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationService authenticationService;

    /**
     * 使用微信小程序一次性 code 登录。
     */
    @PostMapping("/wechat/login")
    @Operation(summary = "微信登录")
    public ApiResponse<WechatLoginResponse> login(
            @Valid @RequestBody WechatLoginRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        AuthenticationResult result = authenticationService.login(
                new WechatLoginCommand(request.code()));
        return ApiResponse.success(toLoginResponse(result), traceId);
    }

    /**
     * 注销当前登录态。
     */
    @PostMapping("/logout")
    @Operation(summary = "登出")
    public ApiResponse<Void> logout(
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        authenticationService.logout();
        return ApiResponse.success(null, traceId);
    }

    /**
     * 查询当前登录用户。
     */
    @GetMapping("/me")
    @Operation(summary = "查询当前用户")
    public ApiResponse<AuthUserResponse> me(
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toUserResponse(authenticationService.currentUser()),
                traceId);
    }

    private WechatLoginResponse toLoginResponse(
            AuthenticationResult result) {
        AuthUser user = result.user();
        return new WechatLoginResponse(
                result.token().value(),
                TOKEN_TYPE,
                result.token().expiresInSeconds(),
                new WechatLoginUserResponse(
                        Long.toString(user.id()),
                        user.nickname(),
                        user.avatarUrl(),
                        result.newUser()));
    }

    private AuthUserResponse toUserResponse(AuthUser user) {
        return new AuthUserResponse(
                Long.toString(user.id()),
                user.nickname(),
                user.avatarUrl());
    }
}
