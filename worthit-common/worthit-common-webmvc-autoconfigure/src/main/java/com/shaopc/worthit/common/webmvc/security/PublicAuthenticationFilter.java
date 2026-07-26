package com.shaopc.worthit.common.webmvc.security;

import cn.dev33.satoken.exception.SaTokenException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * 校验 Servlet 公网接口所需的用户登录态。
 */
public final class PublicAuthenticationFilter extends OncePerRequestFilter {

    private final UserLoginVerifier userLoginVerifier;
    private final PublicRequestAuthorizationPolicy authorizationPolicy;
    private final ServletApiErrorWriter errorWriter;

    /**
     * 创建公网用户鉴权过滤器。
     *
     * @param userLoginVerifier 用户登录态校验器
     * @param authorizationPolicy 公网请求登录策略
     * @param errorWriter 统一错误写入器
     */
    public PublicAuthenticationFilter(
            UserLoginVerifier userLoginVerifier,
            PublicRequestAuthorizationPolicy authorizationPolicy,
            ServletApiErrorWriter errorWriter) {
        this.userLoginVerifier = Objects.requireNonNull(
                userLoginVerifier, "用户登录态校验器不能为空");
        this.authorizationPolicy = Objects.requireNonNull(
                authorizationPolicy, "公网请求登录策略不能为空");
        this.errorWriter =
                Objects.requireNonNull(errorWriter, "错误写入器不能为空");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isApiPath(request.getRequestURI())
                && authorizationPolicy.requiresLogin(request.getRequestURI())) {
            try {
                userLoginVerifier.verify();
            } catch (SaTokenException exception) {
                errorWriter.write(
                        request,
                        response,
                        HttpStatus.UNAUTHORIZED,
                        SecurityErrorCode.AUTH_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !isApiPath(path) && !isInternalPath(path);
    }

    private static boolean isApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    private static boolean isInternalPath(String path) {
        return path.equals("/internal") || path.startsWith("/internal/");
    }
}
