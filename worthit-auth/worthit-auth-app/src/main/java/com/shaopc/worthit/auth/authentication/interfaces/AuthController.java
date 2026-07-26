package com.shaopc.worthit.auth.authentication.interfaces;

import com.shaopc.worthit.auth.authentication.application.AuthenticationResult;
import com.shaopc.worthit.auth.authentication.application.AuthenticationService;
import com.shaopc.worthit.auth.authentication.application.PasswordAuthenticationService;
import com.shaopc.worthit.auth.authentication.application.PasswordLoginCommand;
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
@Tag(name = "认证", description = "登录、登出与当前用户")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationService authenticationService;
    private final PasswordAuthenticationService passwordAuthenticationService;

    /**
     * 使用微信小程序一次性 code 登录。
     */
    @PostMapping("/wechat/login")
    @Operation(summary = "微信登录")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody WechatLoginRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        AuthenticationResult result = authenticationService.login(
                new WechatLoginCommand(request.code()));
        return ApiResponse.success(
                toLoginResponse(result, result.newUser()), traceId);
    }

    /**
     * 使用账号密码登录，供 App、H5 和本地联调使用。
     */
    @PostMapping("/password/login")
    @Operation(summary = "账号密码登录")
    public ApiResponse<LoginResponse> passwordLogin(
            @Valid @RequestBody PasswordLoginRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        AuthenticationResult result = passwordAuthenticationService.login(
                new PasswordLoginCommand(
                        request.username(), request.password()));
        return ApiResponse.success(
                toLoginResponse(result, null), traceId);
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

    private LoginResponse toLoginResponse(
            AuthenticationResult result,
            Boolean isNewUser) {
        AuthUser user = result.user();
        return new LoginResponse(
                result.token().value(),
                TOKEN_TYPE,
                result.token().expiresInSeconds(),
                new LoginUserResponse(
                        Long.toString(user.id()),
                        user.nickname(),
                        user.avatarUrl(),
                        isNewUser));
    }

    private AuthUserResponse toUserResponse(AuthUser user) {
        return new AuthUserResponse(
                Long.toString(user.id()),
                user.nickname(),
                user.avatarUrl());
    }
}
