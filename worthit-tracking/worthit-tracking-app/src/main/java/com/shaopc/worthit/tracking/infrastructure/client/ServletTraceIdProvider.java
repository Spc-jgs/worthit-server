package com.shaopc.worthit.tracking.infrastructure.client;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 从当前可信 Servlet 请求中传播 TraceId，无请求上下文时生成新标识。
 */
public final class ServletTraceIdProvider implements TraceIdProvider {

    private final TraceIdGenerator traceIdGenerator;

    /**
     * 创建 Servlet TraceId 提供器。
     *
     * @param traceIdGenerator 无可信请求上下文时的标识生成器
     */
    public ServletTraceIdProvider(TraceIdGenerator traceIdGenerator) {
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
    }

    @Override
    public String currentTraceId() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            Object traceId = attributes.getRequest()
                    .getAttribute(SecurityHeaderNames.TRACE_ID);
            if (traceId instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        String generated = traceIdGenerator.generate();
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("TraceId不能为空");
        }
        return generated;
    }
}
