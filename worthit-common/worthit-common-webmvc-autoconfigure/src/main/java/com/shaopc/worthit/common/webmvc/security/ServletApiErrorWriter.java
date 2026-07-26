package com.shaopc.worthit.common.webmvc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.core.error.ErrorCode;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 向 Servlet 响应写入统一 API 错误信封。
 */
public final class ServletApiErrorWriter {

    private final ObjectMapper objectMapper;
    private final TraceIdGenerator traceIdGenerator;

    /**
     * 创建 Servlet API 错误写入器。
     *
     * @param objectMapper 统一响应序列化器
     * @param traceIdGenerator TraceId 生成器
     */
    public ServletApiErrorWriter(
            ObjectMapper objectMapper,
            TraceIdGenerator traceIdGenerator) {
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "ObjectMapper不能为空");
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
    }

    /**
     * 写入带可信 TraceId 的统一错误响应。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param status HTTP 状态
     * @param errorCode 稳定错误码
     * @throws IOException 响应写入失败
     */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode errorCode) throws IOException {
        String traceId = currentOrGeneratedTraceId(request);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(SecurityHeaderNames.TRACE_ID, traceId);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error(errorCode, traceId, List.of()));
    }

    private String currentOrGeneratedTraceId(HttpServletRequest request) {
        Object current = request.getAttribute(SecurityHeaderNames.TRACE_ID);
        if (current instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String generated = requireTraceId(traceIdGenerator.generate());
        request.setAttribute(SecurityHeaderNames.TRACE_ID, generated);
        return generated;
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException("TraceId不能为空");
        }
        return traceId;
    }
}
