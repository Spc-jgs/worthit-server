package com.shaopc.worthit.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 将 Gateway 登录态异常转换为统一且不泄露认证细节的响应。
 */
@Component
public final class GatewaySecurityErrorWriter {

    private final ObjectMapper objectMapper;
    private final TraceIdGenerator traceIdGenerator;

    /**
     * 创建 Gateway 认证错误写入器。
     *
     * @param objectMapper     统一响应序列化器
     * @param traceIdGenerator TraceId 生成器
     */
    public GatewaySecurityErrorWriter(
            ObjectMapper objectMapper,
            TraceIdGenerator traceIdGenerator) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper, "ObjectMapper不能为空");
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
    }

    /**
     * 写入未登录响应元数据并返回 JSON 响应体。
     *
     * @param ignored 登录态异常，仅用于触发统一响应，不向客户端暴露
     * @return 统一 JSON 响应体
     */
    public String unauthorized(Throwable ignored) {
        String traceId = requireTraceId(traceIdGenerator.generate());
        ServerHttpResponse response = (ServerHttpResponse)
                SaHolder.getResponse().getSource();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(SecurityHeaderNames.TRACE_ID, traceId);
        try {
            return objectMapper.writeValueAsString(ApiResponse.error(
                    SecurityErrorCode.AUTH_UNAUTHORIZED,
                    traceId,
                    List.of()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("认证错误响应序列化失败", exception);
        }
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException("TraceId不能为空");
        }
        return traceId;
    }
}
