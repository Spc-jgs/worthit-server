package com.shaopc.worthit.reminder.app.security;

import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.security.sametoken.SameTokenVerifier;
import com.shaopc.worthit.common.web.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 校验提醒服务公网和内部接口请求来自可信 Gateway。
 */
@Component
public final class TrustedSourceFilter extends OncePerRequestFilter {

    private final SameTokenVerifier sameTokenVerifier;
    private final TraceIdGenerator traceIdGenerator;
    private final ObjectMapper objectMapper;
    private final UserLoginVerifier userLoginVerifier;

    /**
     * 创建可信来源过滤器。
     *
     * @param sameTokenVerifier Same-Token 校验器
     * @param traceIdGenerator  TraceId 生成器
     * @param objectMapper      统一响应序列化器
     * @param userLoginVerifier 用户登录态校验器
     */
    public TrustedSourceFilter(
            SameTokenVerifier sameTokenVerifier,
            TraceIdGenerator traceIdGenerator,
            ObjectMapper objectMapper,
            UserLoginVerifier userLoginVerifier) {
        this.sameTokenVerifier = Objects.requireNonNull(
                sameTokenVerifier, "Same-Token校验器不能为空");
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
        this.objectMapper = Objects.requireNonNull(
                objectMapper, "ObjectMapper不能为空");
        this.userLoginVerifier = Objects.requireNonNull(
                userLoginVerifier, "用户登录态校验器不能为空");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            sameTokenVerifier.verify(request.getHeader(SecurityHeaderNames.SAME_TOKEN));
        } catch (SaTokenException exception) {
            writeForbidden(response, traceIdGenerator.generate());
            return;
        }

        String traceId = trustedOrGeneratedTraceId(request);
        response.setHeader(SecurityHeaderNames.TRACE_ID, traceId);
        if (isApiPath(request.getRequestURI())) {
            try {
                userLoginVerifier.verify();
            } catch (SaTokenException exception) {
                writeUnauthorized(response, traceId);
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

    private String trustedOrGeneratedTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(SecurityHeaderNames.TRACE_ID);
        return traceId == null || traceId.isBlank()
                ? requireTraceId(traceIdGenerator.generate())
                : traceId;
    }

    private void writeForbidden(HttpServletResponse response, String traceId)
            throws IOException {
        writeError(
                response,
                HttpStatus.FORBIDDEN,
                SecurityErrorCode.AUTH_FORBIDDEN,
                traceId);
    }

    private void writeUnauthorized(HttpServletResponse response, String traceId)
            throws IOException {
        writeError(
                response,
                HttpStatus.UNAUTHORIZED,
                SecurityErrorCode.AUTH_UNAUTHORIZED,
                traceId);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            SecurityErrorCode errorCode,
            String traceId) throws IOException {
        String requiredTraceId = requireTraceId(traceId);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(SecurityHeaderNames.TRACE_ID, requiredTraceId);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error(
                        errorCode,
                        requiredTraceId,
                        List.of()));
    }

    private static boolean isApiPath(String path) {
        return path.equals("/api") || path.startsWith("/api/");
    }

    private static boolean isInternalPath(String path) {
        return path.equals("/internal") || path.startsWith("/internal/");
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException("TraceId不能为空");
        }
        return traceId;
    }
}
