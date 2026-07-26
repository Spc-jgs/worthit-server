package com.shaopc.worthit.common.webmvc.trace;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.webmvc.security.TrustedRequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * 为 Servlet API 请求建立可信 TraceId。
 */
public final class TrustedTraceIdFilter extends OncePerRequestFilter {

    private final TraceIdGenerator traceIdGenerator;

    /**
     * 创建可信 TraceId 过滤器。
     *
     * @param traceIdGenerator TraceId 生成器
     */
    public TrustedTraceIdFilter(TraceIdGenerator traceIdGenerator) {
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = trustedOrGeneratedTraceId(request);
        request.setAttribute(SecurityHeaderNames.TRACE_ID, traceId);
        response.setHeader(SecurityHeaderNames.TRACE_ID, traceId);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !isApiPath(path) && !isInternalPath(path);
    }

    private String trustedOrGeneratedTraceId(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(
                TrustedRequestAttributes.TRUSTED_SOURCE))) {
            String traceId = request.getHeader(SecurityHeaderNames.TRACE_ID);
            if (traceId != null && !traceId.isBlank()) {
                return traceId;
            }
        }
        return requireTraceId(traceIdGenerator.generate());
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
