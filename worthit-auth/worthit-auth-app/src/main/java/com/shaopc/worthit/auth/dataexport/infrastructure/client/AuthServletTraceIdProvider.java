package com.shaopc.worthit.auth.dataexport.infrastructure.client;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/** 从当前 Auth Servlet 请求传播 TraceId，无上下文时生成新标识。 */
public final class AuthServletTraceIdProvider implements TraceIdProvider {

    private final TraceIdGenerator traceIdGenerator;

    /** 创建 Auth TraceId 提供器。 */
    public AuthServletTraceIdProvider(TraceIdGenerator traceIdGenerator) {
        this.traceIdGenerator = Objects.requireNonNull(traceIdGenerator);
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
